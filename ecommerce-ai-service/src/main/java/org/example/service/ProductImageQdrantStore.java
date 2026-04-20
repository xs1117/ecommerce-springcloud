package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.example.config.AiProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ProductImageQdrantStore {

    private static final int VECTOR_SIZE = 64;

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final AtomicBoolean collectionReady = new AtomicBoolean(false);

    public ProductImageQdrantStore(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    public boolean isAvailable() {
        return aiProperties.getImageCompare().isEnabled()
                && aiProperties.getImageCompare().isQdrantEnabled()
                && StringUtils.hasText(resolveQdrantUrl());
    }

    public void ensureCollection() {
        if (!isAvailable() || collectionReady.get()) {
            return;
        }
        synchronized (collectionReady) {
            if (collectionReady.get()) {
                return;
            }
            try {
                String url = joinUrl(resolveQdrantUrl(), "/collections/" + collectionName());
                Map<String, Object> body = Map.of("vectors", Map.of("size", VECTOR_SIZE, "distance", "Cosine"));
                HttpResponse<String> response = send(requestBuilder(url)
                        .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                        .build());
                if ((response.statusCode() >= 200 && response.statusCode() < 300) || response.statusCode() == 409) {
                    collectionReady.set(true);
                    return;
                }
                throw new IllegalStateException("create product image collection failed: " + response.statusCode() + " " + response.body());
            } catch (Exception ex) {
                throw new IllegalStateException("Unable to initialize product image Qdrant collection", ex);
            }
        }
    }

    public void clearCollection() {
        if (!isAvailable()) {
            return;
        }
        try {
            String url = joinUrl(resolveQdrantUrl(), "/collections/" + collectionName());
            HttpResponse<String> response = send(requestBuilder(url)
                    .DELETE()
                    .build());
            if ((response.statusCode() >= 200 && response.statusCode() < 300) || response.statusCode() == 404) {
                collectionReady.set(false);
                ensureCollection();
                return;
            }
            throw new IllegalStateException("clear product image index failed: " + response.statusCode() + " " + response.body());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to clear product image index", ex);
        }
    }

    public long count() {
        if (!isAvailable()) {
            return 0L;
        }
        ensureCollection();
        try {
            String url = joinUrl(resolveQdrantUrl(), "/collections/" + collectionName() + "/points/count");
            Map<String, Object> body = Map.of("exact", true);
            HttpResponse<String> response = send(requestBuilder(url)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return 0L;
            }
            JsonNode root = objectMapper.readTree(response.body());
            return root.path("result").path("count").asLong(0L);
        } catch (Exception ex) {
            return 0L;
        }
    }

    public void upsert(List<Point> points) {
        if (!isAvailable() || points == null || points.isEmpty()) {
            return;
        }
        ensureCollection();
        try {
            List<Map<String, Object>> records = new ArrayList<>();
            for (Point point : points) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", toPointId(point.productId()));
                item.put("vector", point.vector());
                item.put("payload", point.payload());
                records.add(item);
            }
            String url = joinUrl(resolveQdrantUrl(), "/collections/" + collectionName() + "/points?wait=true");
            Map<String, Object> body = Map.of("points", records);
            HttpResponse<String> response = send(requestBuilder(url)
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("upsert product image index failed: " + response.statusCode() + " " + response.body());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to upsert product image index", ex);
        }
    }

    public void deleteByProductIds(List<String> productIds) {
        if (!isAvailable() || productIds == null || productIds.isEmpty()) {
            return;
        }
        ensureCollection();
        try {
            List<Object> ids = productIds.stream().filter(StringUtils::hasText).map(this::toPointId).toList();
            if (ids.isEmpty()) {
                return;
            }
            String url = joinUrl(resolveQdrantUrl(), "/collections/" + collectionName() + "/points/delete?wait=true");
            Map<String, Object> body = Map.of("points", ids);
            HttpResponse<String> response = send(requestBuilder(url)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("delete product image index points failed: " + response.statusCode() + " " + response.body());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to delete product image index points", ex);
        }
    }

    public List<SearchHit> search(float[] queryVector, int limit) {
        if (!isAvailable() || queryVector == null || queryVector.length != VECTOR_SIZE) {
            return List.of();
        }
        ensureCollection();
        try {
            String url = joinUrl(resolveQdrantUrl(), "/collections/" + collectionName() + "/points/search");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("vector", queryVector);
            body.put("limit", Math.max(1, limit));
            body.put("with_payload", true);
            body.put("with_vector", false);
            HttpResponse<String> response = send(requestBuilder(url)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode result = root.path("result");
            List<SearchHit> hits = new ArrayList<>();
            if (result.isArray()) {
                for (JsonNode item : result) {
                    JsonNode payload = item.path("payload");
                    Map<String, Object> map = objectMapper.convertValue(payload, new TypeReference<>() {
                    });
                    hits.add(new SearchHit(item.path("score").asDouble(0d), map));
                }
            }
            return hits;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private HttpRequest.Builder requestBuilder(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json");
        String apiKey = resolveQdrantApiKey();
        if (StringUtils.hasText(apiKey)) {
            builder.header("api-key", apiKey);
        }
        return builder;
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private Object toPointId(String productId) {
        if (!StringUtils.hasText(productId)) {
            return 1L;
        }
        try {
            return Long.parseLong(productId);
        } catch (Exception ignored) {
            return Math.abs((long) productId.hashCode()) + 1L;
        }
    }

    private String resolveQdrantUrl() {
        String self = aiProperties.getImageCompare().getQdrantUrl();
        if (StringUtils.hasText(self)) {
            return self.trim();
        }
        return aiProperties.getRag().getQdrantUrl();
    }

    private String resolveQdrantApiKey() {
        String self = aiProperties.getImageCompare().getQdrantApiKey();
        if (StringUtils.hasText(self)) {
            return self.trim();
        }
        return aiProperties.getRag().getQdrantApiKey();
    }

    private String collectionName() {
        String value = aiProperties.getImageCompare().getQdrantCollection();
        return StringUtils.hasText(value) ? value.trim() : "ecommerce_ai_product_image_index";
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

    public record Point(String productId, float[] vector, Map<String, Object> payload) {
    }

    public record SearchHit(double score, Map<String, Object> payload) {
    }
}


