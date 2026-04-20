package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class VisionRecognitionService {

	private static final Logger log = LoggerFactory.getLogger(VisionRecognitionService.class);

	private final AiProperties aiProperties;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;

	public VisionRecognitionService(AiProperties aiProperties, ObjectMapper objectMapper) {
		this.aiProperties = aiProperties;
		this.objectMapper = objectMapper;
		int timeout = Math.max(aiProperties.getChatTimeoutMillis(), 1000);
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Math.min(timeout, 5000));
		requestFactory.setReadTimeout(timeout);
		this.restClient = RestClient.builder()
				.requestFactory(requestFactory)
				.build();
	}

	public VisionResult recognizeProduct(String imageUrl, String userMessage) {
		if (!StringUtils.hasText(imageUrl)) {
			return VisionResult.empty();
		}
		AiProperties.VisionProperties vision = aiProperties.getVision();
		if (!vision.isEnabled() || !StringUtils.hasText(aiProperties.getApiKey())) {
			return VisionResult.fallback(userMessage);
		}

		String absoluteImageUrl = toAbsoluteUrl(imageUrl.trim());
		String response;
		try {
			response = restClient.post()
					.uri(buildChatUri())
					.contentType(MediaType.APPLICATION_JSON)
					.header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(aiProperties.getApiKey()))
					.body(buildVisionRequest(absoluteImageUrl, userMessage))
					.retrieve()
					.toEntity(String.class)
					.getBody();
		} catch (Exception ex) {
			log.warn("Vision recognition call failed. imageUrl={}, error={}", absoluteImageUrl, ex.getMessage());
			return VisionResult.fallback(userMessage);
		}
		return parseVisionResult(response, userMessage);
	}

	private URI buildChatUri() {
		String baseUrl = normalizeBaseUrl(aiProperties.getBaseUrl());
		String path = normalizePath(aiProperties.getChatPath());
		return URI.create(baseUrl + path);
	}

	private Map<String, Object> buildVisionRequest(String imageUrl, String userMessage) {
		AiProperties.VisionProperties vision = aiProperties.getVision();
		String prompt = StringUtils.hasText(userMessage)
				? "用户补充描述：" + userMessage.trim() + "\n" + vision.getPrompt()
				: vision.getPrompt();

		Map<String, Object> textContent = Map.of("type", "text", "text", prompt);
		Map<String, Object> imageContent = Map.of("type", "image_url", "image_url", Map.of("url", imageUrl));

		Map<String, Object> user = new LinkedHashMap<>();
		user.put("role", "user");
		user.put("content", List.of(textContent, imageContent));

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", StringUtils.hasText(vision.getModel()) ? vision.getModel().trim() : aiProperties.getModel());
		body.put("messages", List.of(user));
		body.put("stream", false);
		body.put("temperature", 0.1);
		return body;
	}

	private VisionResult parseVisionResult(String responseBody, String userMessage) {
		if (!StringUtils.hasText(responseBody)) {
			return VisionResult.fallback(userMessage);
		}
		try {
			JsonNode root = objectMapper.readTree(responseBody);
			String content = readText(root, "choices.0.message.content");
			if (!StringUtils.hasText(content)) {
				content = readText(root, "data.choices.0.message.content");
			}
			if (!StringUtils.hasText(content)) {
				return VisionResult.fallback(userMessage);
			}

			String trimmed = content.trim();
			if (trimmed.startsWith("{")) {
				JsonNode data = objectMapper.readTree(trimmed);
				String productName = asText(data.path("productName"));
				String keyword = asText(data.path("keyword"));
				String confidence = asText(data.path("confidence"));
				if (!StringUtils.hasText(keyword)) {
					keyword = productName;
				}
				if (!StringUtils.hasText(keyword)) {
					return VisionResult.fallback(userMessage);
				}
				return new VisionResult(productName, keyword, confidence);
			}
			return new VisionResult(trimmed, trimmed, "");
		} catch (Exception ex) {
			log.warn("Vision response parse failed. error={}", ex.getMessage());
			return VisionResult.fallback(userMessage);
		}
	}

	private String readText(JsonNode root, String path) {
		JsonNode current = root;
		for (String segment : path.split("\\.")) {
			if (current == null || current.isMissingNode() || current.isNull()) {
				return "";
			}
			if (segment.matches("\\d+")) {
				int index = Integer.parseInt(segment);
				if (!current.isArray() || current.size() <= index) {
					return "";
				}
				current = current.get(index);
			} else {
				current = current.path(segment);
			}
		}
		return asText(current);
	}

	private String asText(JsonNode node) {
		if (node == null || node.isNull() || node.isMissingNode()) {
			return "";
		}
		if (node.isTextual()) {
			return node.asText().trim();
		}
		return node.toString().trim();
	}

	private String toAbsoluteUrl(String value) {
		if (value.startsWith("http://") || value.startsWith("https://")) {
			return value;
		}
		String base = normalizeBaseUrl(aiProperties.getChatServiceBaseUrl());
		String path = value.startsWith("/") ? value : "/" + value;
		return base + path;
	}

	private String normalizeBaseUrl(String value) {
		String base = StringUtils.hasText(value) ? value.trim() : "http://localhost:8080";
		while (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		return base;
	}

	private String normalizePath(String path) {
		String value = StringUtils.hasText(path) ? path.trim() : "/v1/chat/completions";
		if (!value.startsWith("/")) {
			value = "/" + value;
		}
		return value;
	}

	private String buildAuthorizationHeader(String apiKey) {
		String trimmed = apiKey == null ? "" : apiKey.trim();
		if (trimmed.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
			trimmed = trimmed.substring(7).trim();
		}
		return "Bearer " + trimmed;
	}

	public record VisionResult(String productName, String keyword, String confidence) {

		public static VisionResult empty() {
			return new VisionResult("", "", "");
		}

		public static VisionResult fallback(String userMessage) {
			if (StringUtils.hasText(userMessage)) {
				String normalized = userMessage.trim();
				return new VisionResult(normalized, normalized, "");
			}
			return empty();
		}

		public boolean hasKeyword() {
			return StringUtils.hasText(keyword);
		}
	}
}

