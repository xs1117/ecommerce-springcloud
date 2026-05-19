package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductImageCompareService {

    private static final Logger log = LoggerFactory.getLogger(ProductImageCompareService.class);
    private static final int HASH_SIZE = 9;
    private static final int HASH_BITS = 64;

    private final AiProperties aiProperties;
    private final ProductImageQdrantStore qdrantStore;
    private final ObjectMapper objectMapper;

    public ProductImageCompareService(AiProperties aiProperties,
                                      ProductImageQdrantStore qdrantStore,
                                      ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.qdrantStore = qdrantStore;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> compare(String uploadedImageUrl,
                                             List<Map<String, Object>> productCandidates,
                                             int topK,
                                             double minimumScore) {
        // Kept for compatibility; persistent index search is used in main flow.
        return compareAgainstIndexedProducts(uploadedImageUrl, topK, minimumScore);
    }

    public void resetIndex() {
        if (!qdrantStore.isAvailable()) {
            return;
        }
        qdrantStore.clearCollection();
    }

    public int indexedSize() {
        if (!qdrantStore.isAvailable()) {
            return 0;
        }
        return (int) qdrantStore.count();
    }

    public void upsertIndexBatch(Collection<Map<String, Object>> snapshots) {
        if (!qdrantStore.isAvailable() || snapshots == null || snapshots.isEmpty()) {
            return;
        }
        List<ProductImageQdrantStore.Point> upserts = new ArrayList<>();
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
            Long hash = computeDHash(imageUrl);
            if (hash == null) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("productId", id);
            payload.put("title", asText(snapshot.get("title")));
            payload.put("price", snapshot.get("price"));
            payload.put("imageUrl", imageUrl);
            payload.put("storeName", asText(snapshot.get("storeName")));
            payload.put("status", status);
            upserts.add(new ProductImageQdrantStore.Point(id, toVector(hash), payload));
        }
        if (!deletes.isEmpty()) {
            qdrantStore.deleteByProductIds(deletes);
        }
        if (!upserts.isEmpty()) {
            qdrantStore.upsert(upserts);
        }
    }

    public List<Map<String, Object>> compareAgainstIndexedProducts(String uploadedImageUrl, int topK, double minimumScore) {
        if (!qdrantStore.isAvailable() || !StringUtils.hasText(uploadedImageUrl) || qdrantStore.count() <= 0L) {
            return List.of();
        }
        Long sourceHash = computeDHash(uploadedImageUrl);
        if (sourceHash == null) {
            return List.of();
        }
        float[] queryVector = toVector(sourceHash);
        List<ProductImageQdrantStore.SearchHit> hits = qdrantStore.search(queryVector, Math.max(1, topK * 3));
        List<Map<String, Object>> scored = new ArrayList<>();
        for (ProductImageQdrantStore.SearchHit hit : hits) {
            double score = hit.score();
            if (score < minimumScore) {
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
        int size = Math.min(Math.max(1, topK), scored.size());
        return scored.subList(0, size);
    }

    private float[] toVector(long hash) {
        float[] vector = new float[HASH_BITS];
        for (int i = 0; i < HASH_BITS; i++) {
            boolean bit = ((hash >>> i) & 1L) == 1L;
            vector[i] = bit ? 1.0f : -1.0f;
        }
        return vector;
    }

    private Long computeDHash(String imageUrl) {
        String raw = asText(imageUrl);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        List<String> candidates = buildCandidateUrls(raw);
        for (String candidate : candidates) {
            try {
                BufferedImage source = ImageIO.read(URI.create(candidate).toURL());
                if (source == null) {
                    continue;
                }
                BufferedImage normalized = resizeToGray(source);
                long hash = 0L;
                int bitIndex = 0;
                for (int y = 0; y < HASH_SIZE - 1; y++) {
                    for (int x = 0; x < HASH_SIZE - 1; x++) {
                        int left = normalized.getRaster().getSample(x, y, 0);
                        int right = normalized.getRaster().getSample(x + 1, y, 0);
                        if (left > right) {
                            hash |= (1L << bitIndex);
                        }
                        bitIndex++;
                    }
                }
                return hash;
            } catch (Exception ex) {
                // try next candidate
            }
        }
        log.debug("Compute dHash failed. imageUrl={}", imageUrl);
        return null;
    }

    private List<String> buildCandidateUrls(String value) {
        List<String> candidates = new ArrayList<>();
        String url = asText(value);
        if (!StringUtils.hasText(url)) {
            return candidates;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            candidates.add(url);
            return candidates;
        }
        String imageBase = asText(aiProperties.getImageCompare().getImageBaseUrl());
        String chatBase = asText(aiProperties.getChatServiceBaseUrl());
        String path = url.startsWith("/") ? url : "/" + url;
        if (StringUtils.hasText(imageBase)) {
            candidates.add(trimTrailingSlash(imageBase) + path);
        }
        if (StringUtils.hasText(chatBase) && !chatBase.equals(imageBase)) {
            candidates.add(trimTrailingSlash(chatBase) + path);
        }
        return candidates;
    }

    private String trimTrailingSlash(String value) {
        String trimmed = asText(value);
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private BufferedImage resizeToGray(BufferedImage source) {
        int width = HASH_SIZE;
        int height = HASH_SIZE - 1;
        Image scaled = source.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.drawImage(scaled, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        BufferedImage gray = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        ColorConvertOp op = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
        op.filter(rgb, gray);
        return gray;
    }

    private String toAbsoluteUrl(String value) {
        String url = asText(value);
        if (!StringUtils.hasText(url)) {
            return "";
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        String base = asText(aiProperties.getImageCompare().getImageBaseUrl());
        if (!StringUtils.hasText(base)) {
            base = asText(aiProperties.getChatServiceBaseUrl());
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = url.startsWith("/") ? url : "/" + url;
        return base + path;
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
