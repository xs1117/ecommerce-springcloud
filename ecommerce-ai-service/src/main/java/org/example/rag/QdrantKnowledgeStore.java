package org.example.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class QdrantKnowledgeStore {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final AtomicBoolean collectionReady = new AtomicBoolean(false);

    public QdrantKnowledgeStore(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    public boolean isAvailable() {
        return aiProperties.getRag().isVectorEnabled() && StringUtils.hasText(aiProperties.getRag().getQdrantUrl());
    }

    public void ensureCollection(int vectorSize) {
        if (!isAvailable() || collectionReady.get()) {
            return;
        }
        synchronized (collectionReady) {
            if (collectionReady.get()) {
                return;
            }
            try {
                String collection = collectionName();
                String url = joinUrl(baseUrl(), "/collections/" + collection);
                Map<String, Object> body = new LinkedHashMap<>();
                Map<String, Object> vectors = new LinkedHashMap<>();
                vectors.put("size", Math.max(32, vectorSize));
                vectors.put("distance", "Cosine");
                body.put("vectors", vectors);
                HttpRequest request = requestBuilder(url)
                        .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    collectionReady.set(true);
                    return;
                }
                if (response.statusCode() == 409) {
                    collectionReady.set(true);
                    return;
                }
                throw new IllegalStateException("create qdrant collection failed: " + response.statusCode() + " " + response.body());
            } catch (Exception ex) {
                throw new IllegalStateException("Unable to initialize Qdrant collection", ex);
            }
        }
    }

    public void upsert(List<KnowledgeChunk> chunks, List<float[]> vectors) {
        if (!isAvailable() || chunks == null || chunks.isEmpty()) {
            return;
        }
        if (chunks.size() != vectors.size()) {
            throw new IllegalArgumentException("chunks and vectors size mismatch");
        }
        ensureCollection(vectors.get(0).length);
        try {
            List<Map<String, Object>> points = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunk chunk = chunks.get(i);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("source", chunk.source());
                payload.put("title", chunk.title());
                payload.put("chunkIndex", chunk.index());
                payload.put("content", chunk.content());
                payload.put("requiresConfirmation", chunk.requiresConfirmation());
                payload.put("contentHash", chunk.contentHash());
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("id", chunk.id());
                point.put("vector", vectors.get(i));
                point.put("payload", payload);
                points.add(point);
            }
            Map<String, Object> body = Map.of("points", points);
            HttpRequest request = requestBuilder(joinUrl(baseUrl(), "/collections/" + collectionName() + "/points?wait=true"))
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("qdrant upsert failed: " + response.statusCode() + " " + response.body());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to upsert knowledge chunks", ex);
        }
    }

    public List<RetrievedChunk> search(float[] queryVector, int limit) {
        if (!isAvailable() || queryVector == null || queryVector.length == 0) {
            return List.of();
        }
        ensureCollection(queryVector.length);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("vector", queryVector);
            body.put("limit", Math.max(1, limit));
            body.put("with_payload", true);
            body.put("with_vector", false);
            HttpRequest request = requestBuilder(joinUrl(baseUrl(), "/collections/" + collectionName() + "/points/search"))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("qdrant search failed: " + response.statusCode() + " " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode result = root.path("result");
            List<RetrievedChunk> chunks = new ArrayList<>();
            if (result.isArray()) {
                for (JsonNode item : result) {
                    JsonNode payload = item.path("payload");
                    chunks.add(new RetrievedChunk(
                            item.path("id").asText(""),
                            item.path("score").asDouble(0d),
                            payload.path("source").asText(""),
                            payload.path("title").asText(""),
                            payload.path("content").asText(""),
                            payload.path("requiresConfirmation").asBoolean(false)
                    ));
                }
            }
            return chunks;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to search qdrant", ex);
        }
    }

    private HttpRequest.Builder requestBuilder(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json");
        if (StringUtils.hasText(aiProperties.getRag().getQdrantApiKey())) {
            builder.header("api-key", aiProperties.getRag().getQdrantApiKey());
        }
        return builder;
    }

    private String baseUrl() {
        return aiProperties.getRag().getQdrantUrl();
    }

    private String collectionName() {
        return aiProperties.getRag().getQdrantCollection();
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

    public record RetrievedChunk(String id, double score, String source, String title, String content, boolean requiresConfirmation) {
    }
}


