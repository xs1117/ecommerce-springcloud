package org.example.service;

import org.example.config.AiProperties;
import org.example.security.AuthenticatedUser;
import org.example.service.action.ActionHandler;
import org.example.service.action.ActionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class AiCustomerService {

    private static final Logger log = LoggerFactory.getLogger(AiCustomerService.class);
    private static final Pattern ORDER_NO_PATTERN = Pattern.compile("(?:订单号|订单|order)\\s*[:：#-]?\\s*([A-Za-z0-9-]{6,64})", Pattern.CASE_INSENSITIVE);

    private final ChatClient chatClient;
    private final AiProperties aiProperties;
    private final RagService ragService;
    private final ConfirmationService confirmationService;
    private final ChatServiceClient chatServiceClient;
    private final OrderServiceClient orderServiceClient;
    private final ActionRegistry actionRegistry;
    private final VisionRecognitionService visionRecognitionService;
    private final MerchantCatalogClient merchantCatalogClient;
    private final ProductImageCompareService productImageCompareService;
    private final ProductImageIndexSyncService productImageIndexSyncService;

    public AiCustomerService(ChatClient chatClient,
                             AiProperties aiProperties,
                             RagService ragService,
                             ConfirmationService confirmationService,
                             ChatServiceClient chatServiceClient,
                             OrderServiceClient orderServiceClient,
                             ActionRegistry actionRegistry,
                             VisionRecognitionService visionRecognitionService,
                             MerchantCatalogClient merchantCatalogClient,
                             ProductImageCompareService productImageCompareService,
                             ProductImageIndexSyncService productImageIndexSyncService) {
        this.chatClient = chatClient;
        this.aiProperties = aiProperties;
        this.ragService = ragService;
        this.confirmationService = confirmationService;
        this.chatServiceClient = chatServiceClient;
        this.orderServiceClient = orderServiceClient;
        this.actionRegistry = actionRegistry;
        this.visionRecognitionService = visionRecognitionService;
        this.merchantCatalogClient = merchantCatalogClient;
        this.productImageCompareService = productImageCompareService;
        this.productImageIndexSyncService = productImageIndexSyncService;
    }

    public AiChatResult chat(AuthenticatedUser user, String authorizationHeader, AiChatCommand command) {
        if (command == null) {
            throw new ResponseStatusException(BAD_REQUEST, "请求不能为空");
        }

        String message = StringUtils.hasText(command.message()) ? command.message().trim() : "";
        boolean hasImage = StringUtils.hasText(command.imageUrl());
        if (!StringUtils.hasText(message) && !hasImage) {
            throw new ResponseStatusException(BAD_REQUEST, "message和imageUrl不能同时为空");
        }

        Optional<PendingAction> tokenAction = findPendingAction(user, command);
        if (Boolean.TRUE.equals(command.confirm()) || isConfirm(message)) {
            PendingAction action = tokenAction.orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "确认令牌无效或已过期"));
            Map<String, Object> result = executeAfterSaleAction(authorizationHeader, action);
            confirmationService.invalidate(action);
            return new AiChatResult(
                    aiProperties.getModel(),
                    actionRegistry.executedReply(action.actionType()),
                    false,
                    null,
                    null,
                    true,
                    result,
                    "",
                    null
            );
        }

        if (isCancel(message) && tokenAction.isPresent()) {
            confirmationService.invalidate(tokenAction.get());
            return new AiChatResult(
                    aiProperties.getModel(),
                    "好的，已取消本次操作。如果你仍需要我处理，可重新告诉我订单号和诉求。",
                    false,
                    null,
                    null,
                    false,
                    null,
                    "",
                    null
            );
        }

        String orderNo = resolveOrderNo(command);
        Optional<ActionHandler> actionHandler = StringUtils.hasText(message)
                ? actionRegistry.resolve(message)
                : Optional.empty();

        if (actionHandler.isPresent()) {
            ActionHandler handler = actionHandler.get();
            if (!StringUtils.hasText(orderNo)) {
                return new AiChatResult(
                        aiProperties.getModel(),
                        handler.missingOrderReply(),
                        false,
                        null,
                        null,
                        false,
                        null,
                        "",
                        null
                );
            }
            PendingAction pendingAction = confirmationService.create(user.userId(), orderNo, handler.actionType(), handler.remark());
            Map<String, Object> suggested = new LinkedHashMap<>();
            suggested.put("actionType", handler.actionType());
            suggested.put("orderNo", orderNo);
            return new AiChatResult(
                    aiProperties.getModel(),
                    handler.readyReply(orderNo),
                    true,
                    pendingAction.token(),
                    suggested,
                    false,
                    null,
                    "",
                    null
            );
        }

        if (hasImage) {
            return searchByImageAndReply(command.imageUrl().trim(), message);
        }

        String ragContext = ragService.findContext(message);
        String orderContext = resolveOrderContext(authorizationHeader, orderNo);
        String reply = buildGeneralReplyWithLlm(message, ragContext, orderNo, orderContext);
        return new AiChatResult(
                aiProperties.getModel(),
                reply,
                false,
                null,
                null,
                false,
                null,
                ragContext,
                null
        );
    }

    private AiChatResult searchByImageAndReply(String imageUrl, String message) {
        List<Map<String, Object>> similarProducts = compareWithExistingProducts(imageUrl);
        if (!similarProducts.isEmpty()) {
            String reply = "我已将你上传的图片与平台现有商品图进行比对，找到 " + similarProducts.size() + " 个高相似商品，你可以直接查看下方结果。";
            return new AiChatResult(
                    aiProperties.getModel(),
                    reply,
                    false,
                    null,
                    null,
                    false,
                    null,
                    "",
                    similarProducts
            );
        }

        VisionRecognitionService.VisionResult vision = visionRecognitionService.recognizeProduct(imageUrl, message);
        String keyword = StringUtils.hasText(vision.keyword()) ? vision.keyword().trim() : message;
        if (!StringUtils.hasText(keyword)) {
            return new AiChatResult(
                    aiProperties.getModel(),
                    "我暂时无法识别这张图片里的商品。你可以补充商品名称，或换一张更清晰的图再试。",
                    false,
                    null,
                    null,
                    false,
                    null,
                    "",
                    List.of()
            );
        }

        List<Map<String, Object>> products;
        try {
            List<Map<String, Object>> found = merchantCatalogClient.searchPublicProducts(keyword, 6);
            products = normalizeRecommendedProducts(found);
        } catch (Exception ex) {
            log.warn("Image search product lookup failed. keyword={}, error={}", keyword, ex.getMessage());
            products = List.of();
        }

        if (products.isEmpty()) {
            String productName = StringUtils.hasText(vision.productName()) ? vision.productName() : keyword;
            return new AiChatResult(
                    aiProperties.getModel(),
                    "我识别到可能是【" + productName + "】，但暂时没有找到匹配商品。你可以换个角度拍摄，或补充品牌/型号让我继续帮你找。",
                    false,
                    null,
                    null,
                    false,
                    null,
                    "",
                    List.of()
            );
        }

        String productName = StringUtils.hasText(vision.productName()) ? vision.productName() : keyword;
        String reply = "我识别到可能是【" + productName + "】。已为你匹配到 " + products.size() + " 个相关商品，可直接点击下方卡片查看详情。";
        return new AiChatResult(
                aiProperties.getModel(),
                reply,
                false,
                null,
                null,
                false,
                null,
                "",
                products
        );
    }

    private List<Map<String, Object>> compareWithExistingProducts(String imageUrl) {
        AiProperties.ImageCompareProperties settings = aiProperties.getImageCompare();
        if (settings == null || !settings.isEnabled()) {
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

    private Optional<PendingAction> findPendingAction(AuthenticatedUser user, AiChatCommand command) {
        if (StringUtils.hasText(command.confirmationToken())) {
            return confirmationService.findValidByToken(user.userId(), command.confirmationToken().trim());
        }
        return confirmationService.findLatestValidByUser(user.userId());
    }

    private Map<String, Object> executeAfterSaleAction(String authorizationHeader, PendingAction action) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(BAD_REQUEST, "登录态无效，请重新登录后再试");
        }
        Map<String, Object> conversation = chatServiceClient.openAfterSale(authorizationHeader, action.orderNo(), null);
        if (conversation == null || conversation.get("id") == null) {
            throw new ResponseStatusException(BAD_REQUEST, "创建售后会话失败，请稍后重试");
        }
        Long conversationId = Long.valueOf(String.valueOf(conversation.get("id")));
        Map<String, Object> applied = chatServiceClient.applyAfterSaleAction(
                authorizationHeader,
                conversationId,
                action.actionType(),
                action.remark()
        );
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("conversation", conversation);
        merged.put("afterSale", applied);
        return merged;
    }

    private String buildGeneralReplyWithLlm(String message, String ragContext, String orderNo, String orderContext) {
        String systemPrompt = "你是电商平台AI客服。回答需简洁、礼貌，优先中文。"
                + "若用户需求涉及退货/换货/退款/平台介入等需要执行的动作，提示用户提供订单号并说明会进行二次确认。";
        String userPrompt = "用户问题: " + message + "\n"
                + "已选订单号: " + (StringUtils.hasText(orderNo) ? orderNo : "(未提供)") + "\n"
                + "订单上下文:\n" + (StringUtils.hasText(orderContext) ? orderContext : "(未获取到订单详情)") + "\n"
                + "可用知识库片段:\n" + (StringUtils.hasText(ragContext) ? ragContext : "(无匹配知识)");
        try {
            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
            if (StringUtils.hasText(content)) {
                return content.trim();
            }
        } catch (Exception ex) {
            log.warn("LLM call failed, fallback to deterministic reply. model={}, message={}, ragContextPresent={}, error={}",
                    aiProperties.getModel(),
                    message,
                    StringUtils.hasText(ragContext),
                    ex.getMessage(),
                    ex);
        }
        if (StringUtils.hasText(ragContext)) {
            return "根据客服规则，我建议你参考以下信息：\n" + ragContext;
        }
        return "我已收到你的问题。你可以告诉我更具体的信息（如订单号、商品名称），我会继续帮你处理。";
    }

    private String resolveOrderContext(String authorizationHeader, String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return "";
        }
        try {
            Map<String, Object> detail = orderServiceClient.getOrderDetail(authorizationHeader, orderNo.trim());
            if (detail == null || detail.isEmpty()) {
                return "";
            }
            return summarizeOrderContext(detail);
        } catch (Exception ex) {
            log.warn("Failed to load order detail for AI prompt. orderNo={}, error={}", orderNo, ex.getMessage());
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private String summarizeOrderContext(Map<String, Object> detail) {
        Map<String, Object> order = detail.get("order") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        Object itemsObj = detail.get("items");
        java.util.List<Map<String, Object>> items = itemsObj instanceof java.util.List<?> list
                ? list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList()
                : java.util.List.of();

        String orderNo = asText(order.get("orderNo"));
        String status = asText(order.get("status"));
        String createdAt = asText(order.get("createdAt"));
        String payAmount = asText(order.get("payAmount"));
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(orderNo)) {
            builder.append("订单号：").append(orderNo).append('\n');
        }
        if (StringUtils.hasText(status)) {
            builder.append("订单状态：").append(status).append('\n');
        }
        if (StringUtils.hasText(createdAt)) {
            builder.append("下单时间：").append(createdAt).append('\n');
        }
        if (StringUtils.hasText(payAmount)) {
            builder.append("支付金额：").append(payAmount).append('\n');
        }
        if (!items.isEmpty()) {
            builder.append("商品列表：");
            for (int i = 0; i < items.size(); i++) {
                Map<String, Object> item = items.get(i);
                String title = asText(item.get("title"));
                if (!StringUtils.hasText(title)) {
                    title = asText(item.get("productTitle"));
                }
                if (!StringUtils.hasText(title)) {
                    title = asText(item.get("productName"));
                }
                if (i > 0) {
                    builder.append("；");
                }
                builder.append(title.isBlank() ? "商品" : title);
                String quantity = asText(item.get("quantity"));
                if (StringUtils.hasText(quantity)) {
                    builder.append(" x ").append(quantity);
                }
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String resolveOrderNo(AiChatCommand command) {
        if (StringUtils.hasText(command.orderNo())) {
            return command.orderNo().trim();
        }
        if (!StringUtils.hasText(command.message())) {
            return "";
        }
        Matcher matcher = ORDER_NO_PATTERN.matcher(command.message());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private boolean isCancel(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("取消") || normalized.contains("不用了") || normalized.contains("cancel");
    }

    private boolean isConfirm(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("确认") || normalized.contains("同意") || normalized.contains("ok") || normalized.contains("yes");
    }
}

