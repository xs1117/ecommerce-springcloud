package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductImageSemanticSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductImageSemanticSearchService.class);

    private final AiProperties aiProperties;
    private final ImageSemanticVectorService vectorService;
    private final ProductImageSemanticQdrantStore qdrantStore;
    private final ObjectMapper objectMapper;

    public ProductImageSemanticSearchService(AiProperties aiProperties,
                                             ImageSemanticVectorService vectorService,
                                             ProductImageSemanticQdrantStore qdrantStore,
                                             ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.vectorService = vectorService;
        this.qdrantStore = qdrantStore;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return aiProperties.getSemanticImage().isEnabled() && qdrantStore.isAvailable();
    }

    public void resetIndex() {
        if (!isEnabled()) {
            return;
        }
        qdrantStore.clearCollection();
    }

    public int indexedSize() {
        if (!isEnabled()) {
            return 0;
        }
        return (int) qdrantStore.count();
    }

    public void upsertIndexBatch(Collection<Map<String, Object>> snapshots) {
        if (!isEnabled() || snapshots == null || snapshots.isEmpty()) {
            return;
        }
        List<ProductImageSemanticQdrantStore.Point> upserts = new ArrayList<>();
        List<String> deletes = new ArrayList<>();
        for (Map<String, Object> snapshot : snapshots) {
            if (snapshot == null || snapshot.isEmpty()) {
                continue;
            }
            String id = asText(snapshot.get("id"));
            String status = asText(snapshot.get("status")).toUpperCase();
            String imageUrl = resolvePrimaryImageUrl(asText(snapshot.get("imageUrl")));
            if (!StringUtils.hasText(id)) {
                continue;
            }
            if (!"ON_SHELF".equals(status) || !StringUtils.hasText(imageUrl)) {
                deletes.add(id);
                continue;
            }
            float[] vector = vectorService.embedImage(imageUrl, "");
            if (vector.length == 0) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("productId", id);
            payload.put("title", asText(snapshot.get("title")));
            payload.put("price", snapshot.get("price"));
            payload.put("imageUrl", imageUrl);
            payload.put("storeName", asText(snapshot.get("storeName")));
            payload.put("status", status);
            upserts.add(new ProductImageSemanticQdrantStore.Point(id, vector, payload));
        }
        if (!deletes.isEmpty()) {
            qdrantStore.deleteByProductIds(deletes);
        }
        if (!upserts.isEmpty()) {
            qdrantStore.upsert(upserts);
        }
    }

    public List<Map<String, Object>> searchByImage(String imageUrl, String userMessage) {
        if (!isEnabled() || !StringUtils.hasText(imageUrl) || qdrantStore.count() <= 0L) {
            return List.of();
        }
        AiProperties.SemanticImageProperties settings = aiProperties.getSemanticImage();
        float[] queryVector = vectorService.embedImage(imageUrl, userMessage);
        if (queryVector.length != settings.getVectorSize()) {
            return List.of();
        }
        List<ProductImageSemanticQdrantStore.SearchHit> hits = qdrantStore.search(queryVector, Math.max(1, settings.getTopK() * 3));
        logSearchHitsPreview(hits);
        List<Map<String, Object>> scored = new ArrayList<>();
        for (ProductImageSemanticQdrantStore.SearchHit hit : hits) {
            double score = hit.score();
            if (score < settings.getMinimumScore()) {
                continue;
            }
            Map<String, Object> payload = hit.payload();
            String productId = asText(payload.get("productId"));
            if (!StringUtils.hasText(productId)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", productId);
            item.put("title", asText(payload.get("title")));
            item.put("price", payload.get("price"));
            item.put("imageUrl", asText(payload.get("imageUrl")));
            item.put("storeName", asText(payload.get("storeName")));
            item.put("link", "/product/" + productId);
            item.put("similarityScore", roundScore(score));
            scored.add(item);
        }
        scored.sort(Comparator.comparingDouble((Map<String, Object> item) -> asDouble(item.get("similarityScore"))).reversed());
        int size = Math.min(Math.max(1, settings.getTopK()), scored.size());
        return scored.subList(0, size);
    }

    private void logSearchHitsPreview(List<ProductImageSemanticQdrantStore.SearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            log.info("Semantic image search returned no hits.");
            return;
        }
        int limit = Math.min(5, hits.size());
        List<String> preview = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            ProductImageSemanticQdrantStore.SearchHit hit = hits.get(i);
            Map<String, Object> payload = hit.payload();
            String productId = asText(payload.get("productId"));
            String title = asText(payload.get("title"));
            preview.add(productId + "|" + title + "|" + roundScore(hit.score()));
        }
        log.info("Semantic image search top hits: {}", preview);
    }

    private String resolvePrimaryImageUrl(String value) {
        String raw = asText(value);
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        if (!(raw.startsWith("[") && raw.endsWith("]"))) {
            return raw;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.isArray()) {
                for (JsonNode item : node) {
                    String url = asText(item.asText());
                    if (StringUtils.hasText(url)) {
                        return url;
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("Parse image url array failed. value={}, error={}", raw, ex.getMessage());
        }
        return "";
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(asText(value));
        } catch (Exception ex) {
            return 0.0d;
        }
    }

    private double roundScore(double score) {
        return Math.round(score * 1000d) / 1000d;
    }
}

