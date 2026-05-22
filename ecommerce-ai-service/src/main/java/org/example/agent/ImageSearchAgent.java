package org.example.agent;

import org.example.config.AiProperties;
import org.example.service.MerchantCatalogClient;
import org.example.service.ReplyPolisherService;
import org.example.service.ProductImageCompareService;
import org.example.service.ProductImageIndexSyncService;
import org.example.service.ProductImageSemanticSearchService;
import org.example.service.VisionRecognitionService;
import org.example.service.dto.AiChatResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ImageSearchAgent implements CustomerAgent {

    private static final Logger log = LoggerFactory.getLogger(ImageSearchAgent.class);

    private final AiProperties aiProperties;
    private final VisionRecognitionService visionRecognitionService;
    private final MerchantCatalogClient merchantCatalogClient;
    private final ProductImageCompareService productImageCompareService;
    private final ProductImageIndexSyncService productImageIndexSyncService;
    private final ProductImageSemanticSearchService productImageSemanticSearchService;
    private final ReplyPolisherService replyPolisherService;

    public ImageSearchAgent(AiProperties aiProperties,
                            VisionRecognitionService visionRecognitionService,
                            MerchantCatalogClient merchantCatalogClient,
                            ProductImageCompareService productImageCompareService,
                            ProductImageIndexSyncService productImageIndexSyncService,
                            ProductImageSemanticSearchService productImageSemanticSearchService,
                            ReplyPolisherService replyPolisherService) {
        this.aiProperties = aiProperties;
        this.visionRecognitionService = visionRecognitionService;
        this.merchantCatalogClient = merchantCatalogClient;
        this.productImageCompareService = productImageCompareService;
        this.productImageIndexSyncService = productImageIndexSyncService;
        this.productImageSemanticSearchService = productImageSemanticSearchService;
        this.replyPolisherService = replyPolisherService;
    }

    @Override
    public String agentName() {
        return "image-search-agent";
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public boolean supports(ConversationContext context) {
        return context.hasImage();
    }

    @Override
    public AiChatResult handle(ConversationContext context) {
        if (!context.hasImage()) {
            return emptyResult();
        }
        String imageUrl = context.command().imageUrl().trim();
        String rawMessage = context.message();
        // Strip any HTML-like tags (e.g. <think>, <div>, etc.) and trim
        String sanitizedMessage = rawMessage == null ? "" : rawMessage.replaceAll("(?i)<[^>]+>", "").trim();
        // remove accidental JSON-only payloads or leftover markup
        if (sanitizedMessage.startsWith("{") && sanitizedMessage.endsWith("}")) {
            sanitizedMessage = "";
        }
        if (!String.valueOf(rawMessage).equals(sanitizedMessage)) {
            log.info("ImageSearchAgent received image. imageUrl={}, rawMessage=(TRUNCATED){} , sanitizedMessage={}", imageUrl, rawMessage, sanitizedMessage);
        } else {
            log.info("ImageSearchAgent received image. imageUrl={}, message={}", imageUrl, sanitizedMessage);
        }
        List<Map<String, Object>> similarProducts = compareWithExistingProducts(imageUrl);
        log.info("Image compare result count={}", similarProducts == null ? 0 : similarProducts.size());
        if (similarProducts != null && !similarProducts.isEmpty()) {
            // Log raw matched payload for deeper debugging
            try {
                log.info("Image compare raw matched payload: {}", similarProducts);
            } catch (Exception ex) {
                log.warn("Failed to log raw matched payload: {}", ex.getMessage(), ex);
            }
            String reply = polishReply(
                    "图片搜索-高相似商品",
                    "我已将你上传的图片与平台现有商品图进行比对，找到 " + similarProducts.size() + " 个高相似商品，你可以直接查看下方结果。",
                    sanitizedMessage,
                    "matchedCount=" + similarProducts.size()
            );
            return new AiChatResult(
                    aiProperties.getModel(),
                    reply,
                    polishReply("图片搜索-思考", "已完成图片比对并返回结果", sanitizedMessage, "matchedCount=" + similarProducts.size()),
                    false,
                    null,
                    null,
                    false,
                    null,
                    "",
                    similarProducts
            );
        }

        if (productImageSemanticSearchService != null && productImageSemanticSearchService.isEnabled()) {
            List<Map<String, Object>> semanticProducts = productImageSemanticSearchService.searchByImage(imageUrl, sanitizedMessage);
            if (semanticProducts != null && !semanticProducts.isEmpty()) {
                String reply = polishReply(
                        "图片搜索-语义匹配",
                        "我已理解图片语义，并为你匹配到 " + semanticProducts.size() + " 个相关商品，可直接查看下方结果。",
                        sanitizedMessage,
                        "matchedCount=" + semanticProducts.size()
                );
                return new AiChatResult(
                        aiProperties.getModel(),
                        reply,
                        polishReply("图片搜索-思考", "已完成语义向量检索并返回结果", sanitizedMessage, "matchedCount=" + semanticProducts.size()),
                        false,
                        null,
                        null,
                        false,
                        null,
                        "",
                        semanticProducts
                );
            }
        }

        VisionRecognitionService.VisionResult vision = visionRecognitionService == null
                ? VisionRecognitionService.VisionResult.fallback(sanitizedMessage)
                : visionRecognitionService.recognizeProduct(imageUrl, sanitizedMessage);
        String sanitizedVisionKeyword = "";
        if (vision != null) {
            String vp = vision.productName();
            String vk = vision.keyword();
            log.info("Vision recognition result: productName={}, keyword={}, confidence={}", vp, vk, vision.confidence());
            try {
                log.info("Vision full result: {}", vision);
            } catch (Exception ex) {
                log.warn("Failed to log vision full result: {}", ex.getMessage(), ex);
            }
            // sanitize vision outputs (remove any tags like <think>)
            String sanitizedVisionProductName = vp == null ? "" : vp.replaceAll("(?i)<[^>]+>", "").trim();
            sanitizedVisionKeyword = vk == null ? "" : vk.replaceAll("(?i)<[^>]+>", "").trim();
            if ((vp != null && !vp.equals(sanitizedVisionProductName)) || (vk != null && !vk.equals(sanitizedVisionKeyword))) {
                log.info("Vision sanitized result: productName={}, keyword={}", sanitizedVisionProductName, sanitizedVisionKeyword);
            }
        } else {
            log.info("Vision recognition returned null for imageUrl={}", imageUrl);
        }
        String keyword = StringUtils.hasText(sanitizedVisionKeyword) ? sanitizedVisionKeyword : sanitizedMessage;
        if (!StringUtils.hasText(keyword)) {
            String reply = polishReply(
                    "图片搜索-无法识别",
                    "我暂时无法识别这张图片里的商品。你可以补充商品名称，或换一张更清晰的图再试。",
                    sanitizedMessage,
                    "imageUrl=" + imageUrl
            );
            return new AiChatResult(
                    aiProperties.getModel(),
                    reply,
                    polishReply("图片搜索-思考", "已尝试视觉识别，未找到明确关键词", sanitizedMessage, "imageUrl=" + imageUrl),
                    false,
                    null,
                    null,
                    false,
                    null,
                    "",
                    List.of()
            );
        }

        String productName = (vision != null && StringUtils.hasText(vision.productName())) ? vision.productName() : keyword;
        List<Map<String, Object>> products;
        try {
            List<Map<String, Object>> found = merchantCatalogClient == null
                    ? List.of()
                    : merchantCatalogClient.searchPublicProducts(keyword, 6);
            products = normalizeRecommendedProducts(found);
        } catch (Exception ex) {
            log.warn("Image search product lookup failed. keyword={}, error={}", keyword, ex.getMessage());
            products = List.of();
        }

        if (products.isEmpty()) {
            String reply = polishReply(
                    "图片搜索-未命中",
                    "我识别到可能是【" + productName + "】，但暂时没有找到匹配商品。你可以换个角度拍摄，或补充品牌/型号让我继续帮你找。",
                    sanitizedMessage,
                    "productName=" + productName
            );
            return new AiChatResult(
                    aiProperties.getModel(),
                    reply,
                    polishReply("图片搜索-思考", "已识别候选名称并检索但未命中", sanitizedMessage, "productName=" + productName),
                    false,
                    null,
                    null,
                    false,
                    null,
                    "",
                    List.of()
            );
        }

        String reply = polishReply(
                "图片搜索-匹配商品",
                "我识别到可能是【" + productName + "】。已为你匹配到 " + products.size() + " 个相关商品，可直接点击下方卡片查看详情。",
                sanitizedMessage,
                "productName=" + productName + ", matchedCount=" + products.size()
        );
        return new AiChatResult(
                aiProperties.getModel(),
                reply,
                polishReply("图片搜索-思考", "已识别并匹配到相关商品", sanitizedMessage, "productName=" + productName + ", matchedCount=" + products.size()),
                false,
                null,
                null,
                false,
                null,
                "",
                products
        );
    }

    private AiChatResult emptyResult() {
        String reply = "我暂时无法处理这类请求。";
        return new AiChatResult(aiProperties.getModel(), reply, "", false, null, null, false, null, "", List.of());
    }

    private List<Map<String, Object>> compareWithExistingProducts(String imageUrl) {
        AiProperties.ImageCompareProperties settings = aiProperties.getImageCompare();
        if (settings == null || !settings.isEnabled()) {
            return List.of();
        }
        if (productImageCompareService == null || productImageIndexSyncService == null) {
            return List.of();
        }

        try {
            if (productImageCompareService.indexedSize() <= 0) {
                productImageIndexSyncService.syncFull();
            }
            List<Map<String, Object>> matched = productImageCompareService.compareAgainstIndexedProducts(
                    imageUrl,
                    settings.getTopK(),
                    settings.getMinimumScore());
            // Log top candidate ids and similarity scores to help triage threshold/indexing issues
            if (matched != null && !matched.isEmpty()) {
                int limit = Math.min(matched.size(), 6);
                StringBuilder sb = new StringBuilder("top image compare candidates:");
                for (int i = 0; i < limit; i++) {
                    Map<String, Object> item = matched.get(i);
                    if (item == null) continue;
                    Object id = item.get("id");
                    Object score = item.getOrDefault("similarityScore", item.get("score"));
                    sb.append('\n').append(i + 1).append(". id=").append(id).append(" score=").append(score);
                }
                log.info(sb.toString());
            } else {
                log.info("no image compare candidates returned from compareAgainstIndexedProducts");
            }
            return normalizeRecommendedProducts(matched);
        } catch (Exception ex) {
            log.warn("Image compare failed, fallback to vision keyword search. error={}", ex.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> normalizeRecommendedProducts(List<Map<String, Object>> products) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> item : products) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            String id = asText(item.get("id"));
            if (!StringUtils.hasText(id)) {
                continue;
            }
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", id);
            dto.put("title", asText(item.get("title")));
            dto.put("price", item.get("price"));
            dto.put("imageUrl", asText(item.get("imageUrl")));
            dto.put("storeName", asText(item.get("storeName")));
            dto.put("link", "/product/" + id);
            if (item.containsKey("similarityScore")) {
                dto.put("similarityScore", item.get("similarityScore"));
            }
            normalized.add(dto);
        }
        return normalized;
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String polishReply(String scenario, String draftReply, String userMessage, String facts) {
        return replyPolisherService == null
                ? draftReply
                : replyPolisherService.polish(scenario, draftReply, userMessage, facts, null);
    }
}

