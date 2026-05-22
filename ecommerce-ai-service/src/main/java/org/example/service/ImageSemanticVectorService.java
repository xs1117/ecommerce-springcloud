package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.AiProperties;
import org.example.rag.EmbeddingService;
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
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;

@Service
public class ImageSemanticVectorService {

    private static final Logger log = LoggerFactory.getLogger(ImageSemanticVectorService.class);

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final EmbeddingService embeddingService;

    public ImageSemanticVectorService(AiProperties aiProperties, ObjectMapper objectMapper, EmbeddingService embeddingService) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.embeddingService = embeddingService;
        int timeout = Math.max(aiProperties.getChatTimeoutMillis(), 1000);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.min(timeout, 5000));
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    private boolean isHttpUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("http://") || trimmed.startsWith("https://");
    }

    private boolean isLikelyLocalPath(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.startsWith("file:/")
                || trimmed.startsWith("\\\\")
                || trimmed.matches("^[A-Za-z]:\\\\.*")
                || trimmed.startsWith("/");
    }

    public float[] embedImage(String imageUrl, String userMessage) {
        if (!StringUtils.hasText(imageUrl)) {
            return new float[0];
        }
        AiProperties.SemanticImageProperties settings = aiProperties.getSemanticImage();
        if (!settings.isEnabled() || !StringUtils.hasText(aiProperties.getApiKey())) {
            return new float[0];
        }

        String analysisText = analyzeImageText(imageUrl, userMessage, settings);
        if (StringUtils.hasText(analysisText)) {
            log.info("Semantic image analysis text: {}", truncate(analysisText, 200));
        }
        if (!StringUtils.hasText(analysisText)) {
            analysisText = StringUtils.hasText(userMessage) ? userMessage.trim() : "";
        }
        if (!StringUtils.hasText(analysisText)) {
            return new float[0];
        }
        float[] rawVector = embeddingService.embed(analysisText);
        int targetSize = resolveVectorSize(settings);
        float[] vector = normalizeLength(rawVector, targetSize);
        if (vector.length != targetSize) {
            return new float[0];
        }
        return vector;
    }

    private String analyzeImageText(String imageUrl, String userMessage, AiProperties.SemanticImageProperties settings) {
        String absoluteImageUrl = toAbsoluteUrl(imageUrl.trim());
        String semanticImageRef = toSemanticImageReference(absoluteImageUrl, settings);
        String prompt = buildAnalysisPrompt(userMessage, settings.getPrompt());
        Map<String, Object> body = buildRequest(settings.getModel(), semanticImageRef, prompt);
        String response;
        try {
            response = restClient.post()
                    .uri(buildChatUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(aiProperties.getApiKey()))
                    .body(body)
                    .retrieve()
                    .toEntity(String.class)
                    .getBody();
        } catch (Exception ex) {
            log.warn("Semantic image analysis call failed. imageUrl={}, error={}", absoluteImageUrl, ex.getMessage());
            return "";
        }
        String text = parseAnalysisText(response);
        text = sanitizeAnalysisText(text);
        if (!StringUtils.hasText(text)) {
            log.warn("Semantic image analysis returned empty text. imageUrl={}", absoluteImageUrl);
        }
        return text;
    }

    private String toSemanticImageReference(String absoluteImageUrl, AiProperties.SemanticImageProperties settings) {
        if (!StringUtils.hasText(absoluteImageUrl)) {
            return absoluteImageUrl;
        }
        if (settings.isForceBase64() || isLocalOnlyUrl(absoluteImageUrl) || isLocalFilePath(absoluteImageUrl)) {
            try {
                return toDataUrl(absoluteImageUrl);
            } catch (Exception ex) {
                log.warn("Failed to convert image URL to data URL, fallback to absolute URL. url={}, error={}", absoluteImageUrl, ex.getMessage());
            }
        }
        return absoluteImageUrl;
    }

    private boolean isLocalFilePath(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        if (value.startsWith("file:/")) {
            return true;
        }
        try {
            return Files.exists(Path.of(value));
        } catch (Exception ex) {
            return false;
        }
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
        byte[] bytes;
        String contentType;
        if (imageUrl.startsWith("file:/")) {
            Path path = Path.of(URI.create(imageUrl));
            bytes = Files.readAllBytes(path);
            contentType = Files.probeContentType(path);
            if (!StringUtils.hasText(contentType)) {
                contentType = URLConnection.guessContentTypeFromName(path.getFileName().toString());
            }
        } else if (!isHttpUrl(imageUrl) && isLikelyLocalPath(imageUrl)) {
            Path path = Path.of(imageUrl);
            bytes = Files.readAllBytes(path);
            contentType = Files.probeContentType(path);
            if (!StringUtils.hasText(contentType)) {
                contentType = URLConnection.guessContentTypeFromName(path.getFileName().toString());
            }
        } else {
            URL url = URI.create(imageUrl).toURL();
            URLConnection conn = url.openConnection();
            conn.setConnectTimeout(Math.min(Math.max(aiProperties.getChatTimeoutMillis(), 1000), 5000));
            conn.setReadTimeout(Math.max(aiProperties.getChatTimeoutMillis(), 1000));
            contentType = conn.getContentType();
            if (!StringUtils.hasText(contentType) || contentType.toLowerCase(Locale.ROOT).contains("application/octet-stream")) {
                contentType = URLConnection.guessContentTypeFromName(url.getPath());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = conn.getInputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }
            bytes = out.toByteArray();
            log.info("Converted remote image URL to data URL. sourceUrl={}, contentType={}, bytes={}", imageUrl, contentType, bytes.length);
        }
        if (!StringUtils.hasText(contentType)) {
            contentType = "image/jpeg";
        }
        String dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(bytes);
        if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
            log.info("Converted local image URL to data URL. sourceUrl={}, contentType={}, bytes={}", imageUrl, contentType, bytes.length);
        }
        return dataUrl;
    }

    private String buildAnalysisPrompt(String userMessage, String basePrompt) {
        String prompt = StringUtils.hasText(basePrompt)
                ? basePrompt.trim()
                : "请用简洁中文描述图片中的商品，仅输出商品描述，不要思考过程，不要解释，不要输出<think>等标签。描述覆盖品类、外观、颜色、材质、风格、用途、品牌或图案等关键信息。如图片与用户补充冲突，以图片为准。";
        String hint = StringUtils.hasText(userMessage) ? "用户补充：" + userMessage.trim() + "\n" : "";
        return hint + prompt;
    }

    private Map<String, Object> buildRequest(String model, String imageUrl, String prompt) {
        Map<String, Object> textContent = Map.of("type", "text", "text", prompt);
        Map<String, Object> imageContent = Map.of("type", "image_url", "image_url", Map.of("url", imageUrl));

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("role", "user");
        user.put("content", List.of(textContent, imageContent));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", StringUtils.hasText(model) ? model.trim() : aiProperties.getModel());
        body.put("messages", List.of(user));
        body.put("stream", false);
        body.put("temperature", 0.0);
        return body;
    }

    private String parseAnalysisText(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = readText(root, "choices.0.message.content");
            if (!StringUtils.hasText(content)) {
                content = readText(root, "data.choices.0.message.content");
            }
            if (StringUtils.hasText(content)) {
                log.info("Semantic image raw model output: {}", truncate(content, 300));
            }
            if (!StringUtils.hasText(content)) {
                return "";
            }
            String trimmed = content.trim();
            Optional<JsonNode> maybeJson = extractFirstJsonNode(trimmed);
            if (maybeJson.isPresent()) {
                String extracted = flattenJsonText(maybeJson.get());
                return StringUtils.hasText(extracted) ? extracted : trimmed;
            }
            return trimmed;
        } catch (Exception ex) {
            log.debug("Parse semantic analysis text failed: {}", ex.getMessage());
            return "";
        }
    }

    private String sanitizeAnalysisText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String cleaned = text.replaceAll("(?is)<think>.*?</think>", "");
        cleaned = cleaned.replaceAll("(?is)```.*?```", "");
        cleaned = cleaned.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]+", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    private String flattenJsonText(JsonNode node) {
        List<String> parts = new ArrayList<>();
        String summary = asText(node.path("summary"));
        String description = asText(node.path("description"));
        String text = asText(node.path("text"));
        String keyword = asText(node.path("keyword"));
        if (StringUtils.hasText(summary)) {
            parts.add(summary);
        }
        if (StringUtils.hasText(description)) {
            parts.add(description);
        }
        if (StringUtils.hasText(text)) {
            parts.add(text);
        }
        if (StringUtils.hasText(keyword)) {
            parts.add(keyword);
        }
        JsonNode keywords = node.path("keywords");
        if (keywords.isArray()) {
            for (JsonNode item : keywords) {
                String value = asText(item);
                if (StringUtils.hasText(value)) {
                    parts.add(value);
                }
            }
        } else if (keywords.isTextual()) {
            String value = asText(keywords);
            if (StringUtils.hasText(value)) {
                parts.add(value);
            }
        }
        return String.join(" ", parts).trim();
    }

    private int resolveVectorSize(AiProperties.SemanticImageProperties settings) {
        int configured = settings.getVectorSize();
        if (configured > 0) {
            return Math.max(32, configured);
        }
        return Math.max(32, aiProperties.getRag().getEmbeddingDimension());
    }

    private float[] normalizeLength(float[] vector, int dimension) {
        int size = Math.max(32, dimension);
        if (vector == null || vector.length == 0) {
            return new float[size];
        }
        if (vector.length == size) {
            return vector;
        }
        float[] resized = new float[size];
        System.arraycopy(vector, 0, resized, 0, Math.min(vector.length, size));
        if (vector.length > size) {
            normalizeVector(resized);
        }
        return resized;
    }

    private void normalizeVector(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += value * value;
        }
        if (sum <= 0) {
            return;
        }
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / norm;
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
                            // continue
                        }
                    }
                }
            }
        }
        return Optional.empty();
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

    private URI buildChatUri() {
        String baseUrl = normalizeBaseUrl(aiProperties.getBaseUrl());
        String path = normalizePath(aiProperties.getChatPath());
        return URI.create(baseUrl + path);
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

    private String toAbsoluteUrl(String value) {
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        String imageBase = aiProperties.getImageCompare() == null ? null : aiProperties.getImageCompare().getImageBaseUrl();
        String base = StringUtils.hasText(imageBase) ? normalizeBaseUrl(imageBase) : normalizeBaseUrl(aiProperties.getChatServiceBaseUrl());
        String path = value.startsWith("/") ? value : "/" + value;
        return base + path;
    }

    private String truncate(String value, int maxLen) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, maxLen) + "...";
    }
}
