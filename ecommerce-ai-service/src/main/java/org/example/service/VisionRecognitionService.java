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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Base64;

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
		String visionImageRef = toVisionImageReference(absoluteImageUrl);
		log.info("VisionRecognitionService.recognizeProduct called. imageUrl={}, absoluteUrl={}, visionRefType={}",
				imageUrl,
				absoluteImageUrl,
				visionImageRef.startsWith("data:") ? "data-url" : "remote-url");
		String response;
		try {
			response = restClient.post()
					.uri(buildChatUri())
					.contentType(MediaType.APPLICATION_JSON)
					.header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(aiProperties.getApiKey()))
					.body(buildVisionRequest(visionImageRef, userMessage))
					.retrieve()
					.toEntity(String.class)
					.getBody();
		} catch (Exception ex) {
			log.warn("Vision recognition call failed. imageUrl={}, error={}", absoluteImageUrl, ex.getMessage());
			return VisionResult.fallback(userMessage);
		}
		VisionResult result = parseVisionResult(response, userMessage);
		log.info("Vision parse result: productName={}, keyword={}, confidence={}", result.productName(), result.keyword(), result.confidence());
		return result;
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
			log.info("Vision raw content: {}", trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed);

			// Try to extract first JSON object anywhere in the content
			Optional<JsonNode> maybeJson = extractFirstJsonNode(trimmed);
			if (maybeJson.isPresent()) {
				JsonNode data = maybeJson.get();
				String productName = sanitizeText(asText(data.path("productName")));
				String keyword = sanitizeText(asText(data.path("keyword")));
				String confidence = sanitizeText(asText(data.path("confidence")));
				if (!StringUtils.hasText(keyword)) {
					keyword = productName;
				}
				if (!StringUtils.hasText(keyword)) {
					return VisionResult.empty();
				}
				return new VisionResult(productName, keyword, confidence);
			}

			// No JSON found: fail closed instead of leaking refusal text as product info
			return VisionResult.empty();
		} catch (Exception ex) {
			log.warn("Vision response parse failed. error={}", ex.getMessage(), ex);
			return VisionResult.empty();
		}
	}

	private Optional<JsonNode> extractFirstJsonNode(String text) {
		if (!StringUtils.hasText(text)) {
			return Optional.empty();
		}
		int start = text.indexOf('{');
		int end = text.lastIndexOf('}');
		if (start >= 0 && end >= start) {
			String candidate = text.substring(start, end + 1);
			try {
				JsonNode node = objectMapper.readTree(candidate);
				return Optional.of(node);
			} catch (Exception e) {
				// try to be forgiving: attempt smaller spans (find matching braces)
				int depth = 0;
				for (int i = start; i < text.length(); i++) {
					char c = text.charAt(i);
					if (c == '{') depth++;
					else if (c == '}') depth--;
					if (depth == 0) {
						String cand = text.substring(start, i + 1);
						try {
							JsonNode n2 = objectMapper.readTree(cand);
							return Optional.of(n2);
						} catch (Exception ex) {
							// continue searching
						}
					}
				}
			}
		}
		return Optional.empty();
	}

	private String sanitizeText(String s) {
		if (!StringUtils.hasText(s)) return "";
		// remove tags like <think> and other angle-bracket content, control chars
		String cleaned = s.replaceAll("(?i)<[^>]+>", "");
		cleaned = cleaned.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]+", "");
		cleaned = cleaned.replaceAll("^[\"'\\s]+|[\"'\\s]+$", "").trim();
		// limit length
		if (cleaned.length() > 300) cleaned = cleaned.substring(0, 300).trim();
		return cleaned;
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
		// Prefer image base URL (used for product/image hosting). fall back to chatServiceBaseUrl
		String imageBase = aiProperties.getImageCompare() == null ? null : aiProperties.getImageCompare().getImageBaseUrl();
		String base = StringUtils.hasText(imageBase) ? normalizeBaseUrl(imageBase) : normalizeBaseUrl(aiProperties.getChatServiceBaseUrl());
		String path = value.startsWith("/") ? value : "/" + value;
		String absolute = base + path;
		log.info("toAbsoluteUrl converted '{}' -> '{}' using base '{}'", value, absolute, base);
		return absolute;
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

	private String toVisionImageReference(String absoluteImageUrl) {
		if (!StringUtils.hasText(absoluteImageUrl)) {
			return absoluteImageUrl;
		}
		if (isLocalOnlyUrl(absoluteImageUrl)) {
			try {
				return toDataUrl(absoluteImageUrl);
			} catch (Exception ex) {
				log.warn("Failed to convert local image URL to data URL, fallback to absolute URL. url={}, error={}", absoluteImageUrl, ex.getMessage());
			}
		}
		return absoluteImageUrl;
	}

	private boolean isLocalOnlyUrl(String url) {
		try {
			URI uri = URI.create(url);
			String host = uri.getHost();
			if (!StringUtils.hasText(host)) {
				return false;
			}
			String normalized = host.toLowerCase(Locale.ROOT);
			return "localhost".equals(normalized)
					|| "127.0.0.1".equals(normalized)
					|| normalized.startsWith("10.")
					|| normalized.startsWith("192.168.")
					|| normalized.startsWith("172.16.")
					|| normalized.startsWith("172.17.")
					|| normalized.startsWith("172.18.")
					|| normalized.startsWith("172.19.")
					|| normalized.startsWith("172.2")
					|| normalized.startsWith("172.3");
		} catch (Exception ex) {
			return false;
		}
	}

	private String toDataUrl(String imageUrl) throws Exception {
		URL url = URI.create(imageUrl).toURL();
		URLConnection conn = url.openConnection();
		conn.setConnectTimeout(Math.min(Math.max(aiProperties.getChatTimeoutMillis(), 1000), 5000));
		conn.setReadTimeout(Math.max(aiProperties.getChatTimeoutMillis(), 1000));
		String contentType = conn.getContentType();
		if (!StringUtils.hasText(contentType) || contentType.toLowerCase(Locale.ROOT).contains("application/octet-stream")) {
			contentType = URLConnection.guessContentTypeFromName(url.getPath());
		}
		if (!StringUtils.hasText(contentType)) {
			contentType = "image/jpeg";
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (InputStream in = conn.getInputStream()) {
			byte[] buffer = new byte[8192];
			int len;
			while ((len = in.read(buffer)) != -1) {
				out.write(buffer, 0, len);
			}
		}
		String dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(out.toByteArray());
		log.info("Converted local image URL to data URL. sourceUrl={}, contentType={}, bytes={}", imageUrl, contentType, out.size());
		return dataUrl;
	}

	public record VisionResult(String productName, String keyword, String confidence) {

		public static VisionResult empty() {
			return new VisionResult("", "", "");
		}

		public static VisionResult fallback(String userMessage) {
			return empty();
		}
	}
}

