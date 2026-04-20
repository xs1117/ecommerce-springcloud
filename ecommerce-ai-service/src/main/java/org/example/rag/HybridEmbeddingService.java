package org.example.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HybridEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(HybridEmbeddingService.class);
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]+|[a-z0-9]+", Pattern.CASE_INSENSITIVE);

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public HybridEmbeddingService(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public float[] embed(String text) {
        if (StringUtils.hasText(resolveEmbeddingBaseUrl())) {
            try {
                float[] remote = embedRemote(text);
                if (remote.length > 0) {
                    return normalizeLength(remote, aiProperties.getRag().getEmbeddingDimension());
                }
            } catch (Exception ex) {
                log.warn("Remote embedding failed, fallback to local hash embedding. error={}", ex.getMessage());
            }
        }
        return embedLocally(text, aiProperties.getRag().getEmbeddingDimension());
    }

    private float[] embedRemote(String text) throws Exception {
        String baseUrl = resolveEmbeddingBaseUrl();
        String path = aiProperties.getRag().getEmbeddingPath();
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", aiProperties.getRag().getEmbeddingModel(),
                "input", text
        ));
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(joinUrl(baseUrl, path)))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));
        String apiKey = StringUtils.hasText(aiProperties.getRag().getEmbeddingApiKey())
                ? aiProperties.getRag().getEmbeddingApiKey()
                : aiProperties.getApiKey();
        if (StringUtils.hasText(apiKey)) {
            builder.header("Authorization", apiKey.startsWith("Bearer ") ? apiKey : "Bearer " + apiKey);
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("embedding api returned " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new IllegalStateException("embedding response data is empty");
        }
        JsonNode embeddingNode = data.get(0).path("embedding");
        if (!embeddingNode.isArray()) {
            throw new IllegalStateException("embedding response missing vector");
        }
        float[] vector = new float[embeddingNode.size()];
        for (int i = 0; i < embeddingNode.size(); i++) {
            vector[i] = (float) embeddingNode.get(i).asDouble();
        }
        return vector;
    }

    private float[] embedLocally(String text, int dimension) {
        int size = Math.max(32, dimension);
        float[] vector = new float[size];
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return vector;
        }
        for (String token : tokenize(normalized)) {
            if (token.isBlank()) {
                continue;
            }
            int index = Math.floorMod(token.hashCode(), size);
            float weight = token.length() >= 2 ? 1.2f : 1.0f;
            vector[index] += weight;
        }
        normalizeVector(vector);
        return vector;
    }

    private List<String> tokenize(String normalized) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(normalized);
        while (matcher.find()) {
            String token = matcher.group().toLowerCase(Locale.ROOT);
            if (containsChinese(token)) {
                for (int i = 0; i < token.length(); i++) {
                    tokens.add(String.valueOf(token.charAt(i)));
                    if (i + 1 < token.length()) {
                        tokens.add(token.substring(i, i + 2));
                    }
                }
            } else {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private boolean containsChinese(String value) {
        for (int i = 0; i < value.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(value.charAt(i));
            if (script == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        return StringUtils.trimAllWhitespace(String.valueOf(text))
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsHan}a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String resolveEmbeddingBaseUrl() {
        return StringUtils.hasText(aiProperties.getRag().getEmbeddingBaseUrl())
                ? aiProperties.getRag().getEmbeddingBaseUrl()
                : aiProperties.getBaseUrl();
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

    private float[] normalizeLength(float[] vector, int dimension) {
        int size = Math.max(32, dimension);
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

    private String joinUrl(String baseUrl, String path) {
        if (baseUrl.endsWith("/") && path.startsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + path;
        }
        if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
            return baseUrl + "/" + path;
        }
        return baseUrl + path;
    }
}


