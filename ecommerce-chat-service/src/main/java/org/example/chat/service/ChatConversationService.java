package org.example.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.chat.client.MerchantCatalogClient;
import org.example.chat.client.OrderClient;
import org.example.chat.client.PaymentClient;
import org.example.chat.domain.ChatConversation;
import org.example.chat.domain.ChatMessage;
import org.example.chat.domain.ConversationStatus;
import org.example.chat.domain.MessageType;
import org.example.chat.domain.UserRole;
import org.example.chat.repository.ChatConversationRepository;
import org.example.chat.repository.ChatMessageRepository;
import org.example.chat.security.AuthenticatedUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChatConversationService {

    private static final String BIZ_SHOP_SUPPORT = "SHOP_SUPPORT";
    private static final String BIZ_AFTER_SALE = "AFTER_SALE";

    private static final String AF_STATUS_PENDING = "PENDING";
    private static final String AF_STATUS_RETURN_REQUESTED = "RETURN_REQUESTED";
    private static final String AF_STATUS_EXCHANGE_REQUESTED = "EXCHANGE_REQUESTED";
    private static final String AF_STATUS_MERCHANT_APPROVED_RETURN = "MERCHANT_APPROVED_RETURN";
    private static final String AF_STATUS_MERCHANT_APPROVED_EXCHANGE = "MERCHANT_APPROVED_EXCHANGE";
    private static final String AF_STATUS_MERCHANT_REJECTED = "MERCHANT_REJECTED";
    private static final String AF_STATUS_ADMIN_REQUESTED = "ADMIN_REQUESTED";
    private static final String AF_STATUS_ADMIN_FORCE_CANCELED = "ADMIN_FORCE_CANCELED";
    private static final String AF_STATUS_ADMIN_FORCE_REFUNDED = "ADMIN_FORCE_REFUNDED";

    private static final String ACTION_APPLY_RETURN = "APPLY_RETURN";
    private static final String ACTION_APPLY_EXCHANGE = "APPLY_EXCHANGE";
    private static final String ACTION_REQUEST_ADMIN_INTERVENTION = "REQUEST_ADMIN_INTERVENTION";
    private static final String ACTION_MERCHANT_APPROVE_RETURN = "MERCHANT_APPROVE_RETURN";
    private static final String ACTION_MERCHANT_APPROVE_EXCHANGE = "MERCHANT_APPROVE_EXCHANGE";
    private static final String ACTION_MERCHANT_REJECT = "MERCHANT_REJECT";
    private static final String ACTION_ADMIN_JOIN = "ADMIN_JOIN";
    private static final String ACTION_ADMIN_FORCE_CANCEL = "ADMIN_FORCE_CANCEL";
    private static final String ACTION_ADMIN_FORCE_REFUND = "ADMIN_FORCE_REFUND";

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final MerchantCatalogClient merchantCatalogClient;
    private final OrderClient orderClient;
    private final PaymentClient paymentClient;
    private final ObjectMapper objectMapper;

    public ChatConversationService(ChatConversationRepository conversationRepository,
                                   ChatMessageRepository messageRepository,
                                   MerchantCatalogClient merchantCatalogClient,
                                   OrderClient orderClient,
                                   PaymentClient paymentClient,
                                   ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.merchantCatalogClient = merchantCatalogClient;
        this.orderClient = orderClient;
        this.paymentClient = paymentClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> openConversation(AuthenticatedUser user, Long storeId, Long productId, String sourceType) {
        if (storeId == null || storeId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "店铺ID不能为空");
        }
        Map<String, Object> store = requireStore(storeId);
        Long merchantUserId = toLong(store.get("ownerUserId"));
        if (merchantUserId == null || merchantUserId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "店铺商家信息异常");
        }
        if (Objects.equals(merchantUserId, user.userId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商家无需为自己的店铺新建会话");
        }

        Map<String, Object> product = null;
        if (productId != null && productId > 0) {
            product = requireProduct(productId);
            Long productStoreId = toLong(product.get("storeId"));
            if (productStoreId == null || !Objects.equals(productStoreId, storeId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品不属于该店铺");
            }
        }

        String conversationKey = shopConversationKey(storeId, user.userId());
        final Map<String, Object> productSnapshot = product;
        ChatConversation conversation = conversationRepository.findByConversationKey(conversationKey)
                .orElseGet(() -> createShopConversation(user, store, merchantUserId, sourceType, productSnapshot));

        if (product != null && conversation.getProductId() == null) {
            conversation.setSourceType(normalizeSourceType(sourceType));
            conversation.setProductId(productId);
            conversation.setProductTitle(asText(product.get("title")));
            conversation.setProductImageUrl(asText(product.get("imageUrl")));
            conversation.setProductDescription(asText(product.get("description")));
            conversation.setProductPrice(toBigDecimal(product.get("price")));
            conversation = conversationRepository.save(conversation);
        }

        return toConversationView(conversation, user);
    }

    @Transactional
    public Map<String, Object> openAfterSaleConversation(AuthenticatedUser user, String orderNo, Long storeId) {
        if (!StringUtils.hasText(orderNo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderNo不能为空");
        }
        Map<String, Object> orderDetail = orderClient.orderDetail(orderNo.trim());
        if (orderDetail == null || orderDetail.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> order = (Map<String, Object>) orderDetail.getOrDefault("order", Map.of());
        Long buyerUserId = toLong(order.get("userId"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) orderDetail.getOrDefault("items", List.of());
        if (buyerUserId == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单信息不完整，无法创建售后会话");
        }

        Map<String, Object> selectedItem = pickOrderItem(items, storeId);
        Long selectedStoreId = toLong(selectedItem.get("storeId"));
        if (selectedStoreId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "售后店铺信息缺失");
        }

        Map<String, Object> store = requireStore(selectedStoreId);
        Long merchantUserId = toLong(store.get("ownerUserId"));
        if (merchantUserId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "店铺商家信息异常");
        }

        boolean isBuyer = Objects.equals(user.userId(), buyerUserId);
        boolean isMerchant = Objects.equals(user.userId(), merchantUserId);
        boolean isAdmin = isAdmin(user);
        if (!isBuyer && !isMerchant && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权创建该售后会话");
        }

        String conversationKey = afterSaleConversationKey(orderNo.trim(), selectedStoreId);
        ChatConversation conversation = conversationRepository.findByConversationKey(conversationKey)
                .orElseGet(() -> createAfterSaleConversation(user, orderNo.trim(), buyerUserId, merchantUserId, store, selectedItem));

        if (isAdmin && !Boolean.TRUE.equals(conversation.getAdminJoined())) {
            joinAdmin(conversation, user);
            sendSystemMessage(conversation, user, MessageType.SYSTEM_NOTICE, resolveNickname(user) + "管理员进入会话", Map.of("type", "ADMIN_JOIN"));
        }

        return toConversationView(conversationRepository.save(conversation), user);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listConversations(AuthenticatedUser user) {
        ArrayList<ChatConversation> merged = new ArrayList<>();
        if (isAdmin(user)) {
            merged.addAll(conversationRepository.findByAdminUserIdOrderByLastMessageAtDesc(user.userId()));
            merged.addAll(conversationRepository.findByBizTypeAndAfterSaleStatusOrderByLastMessageAtDesc(BIZ_AFTER_SALE, AF_STATUS_ADMIN_REQUESTED));
        } else {
            merged.addAll(conversationRepository.findByBuyerUserIdOrderByLastMessageAtDesc(user.userId()));
            merged.addAll(conversationRepository.findByMerchantUserIdOrderByLastMessageAtDesc(user.userId()));
        }
        return merged.stream()
                .collect(Collectors.toMap(ChatConversation::getId, conversation -> conversation, (left, ignored) -> left, LinkedHashMap::new))
                .values()
                .stream()
                .filter(conversation -> canAccessConversation(user, conversation))
                .sorted((left, right) -> right.getLastMessageAt().compareTo(left.getLastMessageAt()))
                .map(conversation -> toConversationView(conversation, user))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getConversation(AuthenticatedUser user, Long conversationId) {
        ChatConversation conversation = requireAccessibleConversation(user, conversationId);
        return toConversationView(conversation, user);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listMessages(AuthenticatedUser user, Long conversationId) {
        ChatConversation conversation = requireAccessibleConversation(user, conversationId);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId())
                .stream()
                .map(message -> toMessageView(message, user, conversation))
                .toList();
    }

    @Transactional
    public Map<String, Object> sendMessage(AuthenticatedUser user, Long conversationId, SendMessageRequest request) {
        ChatConversation conversation = requireAccessibleConversation(user, conversationId);
        RoleSide senderSide = resolveRoleSide(user, conversation);
        if (senderSide == RoleSide.UNKNOWN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权发送消息");
        }
        if (senderSide == RoleSide.ADMIN && !Boolean.TRUE.equals(conversation.getAdminJoined())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理员尚未进入会话");
        }
        if (!StringUtils.hasText(request.clientMsgId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clientMsgId不能为空");
        }
        if (!StringUtils.hasText(request.messageType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "messageType不能为空");
        }
        MessageType messageType = parseMessageType(request.messageType());
        String content = StringUtils.hasText(request.content()) ? request.content().trim() : "";
        if (messageType == MessageType.TEXT && !StringUtils.hasText(content)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "消息内容不能为空");
        }
        if (messageType == MessageType.PRODUCT_CARD && !StringUtils.hasText(request.payloadJson())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品卡片载荷不能为空");
        }

        ChatMessage existing = messageRepository.findByConversationIdAndClientMsgId(conversationId, request.clientMsgId()).orElse(null);
        if (existing != null) {
            return toMessageView(existing, user, conversation);
        }

        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setSenderUserId(user.userId());
        message.setSenderRole(senderSide.name());
        message.setSenderNickname(resolveNickname(user));
        message.setClientMsgId(request.clientMsgId());
        message.setMessageType(messageType);
        message.setContent(resolveMessageContent(messageType, content, request.payloadJson()));
        message.setPayloadJson(normalizePayloadJson(request.payloadJson()));
        ChatMessage saved = messageRepository.save(message);

        updateConversationAfterMessage(conversation, senderSide, saved.getMessageType(), saved.getContent(), saved.getCreatedAt());
        conversationRepository.save(conversation);
        return toMessageView(saved, user, conversation);
    }

    @Transactional
    public Map<String, Object> markRead(AuthenticatedUser user, Long conversationId) {
        ChatConversation conversation = requireAccessibleConversation(user, conversationId);
        RoleSide side = resolveRoleSide(user, conversation);
        if (side == RoleSide.BUYER) {
            conversation.setBuyerUnreadCount(0);
        }
        if (side == RoleSide.MERCHANT) {
            conversation.setMerchantUnreadCount(0);
        }
        if (side == RoleSide.ADMIN) {
            conversation.setAdminUnreadCount(0);
        }
        return toConversationView(conversationRepository.save(conversation), user);
    }

    @Transactional
    public Map<String, Object> applyAfterSaleAction(AuthenticatedUser user,
                                                    Long conversationId,
                                                    AfterSaleActionRequest request) {
        if (request == null || !StringUtils.hasText(request.actionType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "actionType不能为空");
        }
        ChatConversation conversation = requireAccessibleConversation(user, conversationId);
        if (!BIZ_AFTER_SALE.equalsIgnoreCase(conversation.getBizType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该会话不是售后会话");
        }
        String actionType = request.actionType().trim().toUpperCase();
        RoleSide side = resolveRoleSide(user, conversation);

        String nextStatus = switch (actionType) {
            case ACTION_APPLY_RETURN -> requireBuyer(side, AF_STATUS_RETURN_REQUESTED);
            case ACTION_APPLY_EXCHANGE -> requireBuyer(side, AF_STATUS_EXCHANGE_REQUESTED);
            case ACTION_REQUEST_ADMIN_INTERVENTION -> requireBuyer(side, AF_STATUS_ADMIN_REQUESTED);
            case ACTION_MERCHANT_APPROVE_RETURN -> requireMerchant(side, AF_STATUS_MERCHANT_APPROVED_RETURN);
            case ACTION_MERCHANT_APPROVE_EXCHANGE -> requireMerchant(side, AF_STATUS_MERCHANT_APPROVED_EXCHANGE);
            case ACTION_MERCHANT_REJECT -> requireMerchant(side, AF_STATUS_MERCHANT_REJECTED);
            case ACTION_ADMIN_JOIN -> handleAdminJoinAction(user, conversation);
            case ACTION_ADMIN_FORCE_CANCEL -> requireAdmin(side, AF_STATUS_ADMIN_FORCE_CANCELED);
            case ACTION_ADMIN_FORCE_REFUND -> requireAdmin(side, AF_STATUS_ADMIN_FORCE_REFUNDED);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的售后操作");
        };

        if (ACTION_ADMIN_JOIN.equals(actionType)) {
            return toConversationView(conversationRepository.save(conversation), user);
        }

        if (ACTION_ADMIN_FORCE_CANCEL.equals(actionType)) {
            if (!StringUtils.hasText(conversation.getOrderNo())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "售后会话缺少订单号");
            }
            try {
                orderClient.forceCancelAfterSale(conversation.getOrderNo());
            } catch (HttpClientErrorException.NotFound ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单服务未发布强制取消售后接口，请重启订单服务");
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "同步订单售后状态失败，请稍后重试");
            }
        }
        if (ACTION_ADMIN_FORCE_REFUND.equals(actionType)) {
            if (!StringUtils.hasText(conversation.getOrderNo())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "售后会话缺少订单号");
            }
            try {
                Map<String, Object> paymentResult = paymentClient.refundByOrderNo(conversation.getOrderNo());
                if (paymentResult == null || !Boolean.TRUE.equals(paymentResult.get("ok"))) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "支付退款失败，无法完成强制退款");
                }
                orderClient.forceRefundAfterSale(conversation.getOrderNo());
            } catch (HttpClientErrorException.NotFound ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单或支付服务未发布强制退款接口，请重启对应服务");
            } catch (ResponseStatusException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "强制退款失败，请稍后重试");
            }
        }

        conversation.setAfterSaleStatus(nextStatus);
        String messageText = buildActionMessage(actionType, resolveNickname(user), request.remark());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actionType", actionType);
        payload.put("afterSaleStatus", nextStatus);
        if (StringUtils.hasText(request.remark())) {
            payload.put("remark", request.remark().trim());
        }
        sendSystemMessage(conversation, user, MessageType.AFTER_SALE_ACTION, messageText, payload);
        return toConversationView(conversationRepository.save(conversation), user);
    }

    private ChatConversation createShopConversation(AuthenticatedUser user,
                                                    Map<String, Object> store,
                                                    Long merchantUserId,
                                                    String sourceType,
                                                    Map<String, Object> product) {
        ChatConversation conversation = new ChatConversation();
        conversation.setConversationKey(shopConversationKey(toLong(store.get("id")), user.userId()));
        conversation.setBizType(BIZ_SHOP_SUPPORT);
        conversation.setStoreId(toLong(store.get("id")));
        conversation.setStoreTitle(asText(store.get("title")));
        conversation.setStoreImageUrl(asText(store.get("storeImageUrl")));
        conversation.setBuyerUserId(user.userId());
        conversation.setBuyerUsername(user.username());
        conversation.setBuyerNickname(resolveNickname(user));
        conversation.setMerchantUserId(merchantUserId);
        conversation.setMerchantUsername(asText(store.get("ownerUsername")));
        conversation.setMerchantNickname(asText(store.get("title")));
        conversation.setSourceType(normalizeSourceType(sourceType));
        if (product != null) {
            conversation.setProductId(toLong(product.get("id")));
            conversation.setProductTitle(asText(product.get("title")));
            conversation.setProductImageUrl(asText(product.get("imageUrl")));
            conversation.setProductDescription(asText(product.get("description")));
            conversation.setProductPrice(toBigDecimal(product.get("price")));
        }
        conversation.setLastMessageType(MessageType.TEXT);
        conversation.setLastMessageText("客服会话已创建");
        conversation.setLastMessageAt(LocalDateTime.now());
        conversation.setBuyerUnreadCount(0);
        conversation.setMerchantUnreadCount(1);
        conversation.setAdminUnreadCount(0);
        conversation.setAdminJoined(false);
        conversation.setStatus(ConversationStatus.ACTIVE);
        return saveConversationWithFallback(conversation);
    }

    private ChatConversation createAfterSaleConversation(AuthenticatedUser user,
                                                         String orderNo,
                                                         Long buyerUserId,
                                                         Long merchantUserId,
                                                         Map<String, Object> store,
                                                         Map<String, Object> selectedItem) {
        ChatConversation conversation = new ChatConversation();
        conversation.setConversationKey(afterSaleConversationKey(orderNo, toLong(selectedItem.get("storeId"))));
        conversation.setBizType(BIZ_AFTER_SALE);
        conversation.setOrderNo(orderNo);
        conversation.setAfterSaleStatus(AF_STATUS_PENDING);
        conversation.setStoreId(toLong(selectedItem.get("storeId")));
        conversation.setStoreTitle(asText(store.get("title")));
        conversation.setStoreImageUrl(asText(store.get("storeImageUrl")));
        conversation.setBuyerUserId(buyerUserId);
        conversation.setBuyerUsername("user-" + buyerUserId);
        conversation.setBuyerNickname("用户" + buyerUserId);
        conversation.setMerchantUserId(merchantUserId);
        conversation.setMerchantUsername(asText(store.get("ownerUsername")));
        conversation.setMerchantNickname(asText(store.get("title")));
        conversation.setSourceType("after-sale");
        conversation.setProductId(toLong(selectedItem.get("productId")));
        conversation.setProductTitle(asText(selectedItem.get("productName")));
        conversation.setProductImageUrl(asText(selectedItem.get("productImageUrl")));
        conversation.setProductDescription(asText(selectedItem.get("productDescription")));
        conversation.setProductPrice(toBigDecimal(selectedItem.get("unitPrice")));
        conversation.setLastMessageType(MessageType.SYSTEM_NOTICE);
        conversation.setLastMessageText("售后会话已创建");
        conversation.setLastMessageAt(LocalDateTime.now());
        conversation.setBuyerUnreadCount(0);
        conversation.setMerchantUnreadCount(0);
        conversation.setAdminUnreadCount(0);
        conversation.setAdminJoined(false);
        conversation.setStatus(ConversationStatus.ACTIVE);

        RoleSide openerSide = resolveRoleSide(user, conversation);
        if (openerSide == RoleSide.BUYER) {
            conversation.setMerchantUnreadCount(1);
        } else if (openerSide == RoleSide.MERCHANT) {
            conversation.setBuyerUnreadCount(1);
        }
        return saveConversationWithFallback(conversation);
    }

    private ChatConversation saveConversationWithFallback(ChatConversation conversation) {
        try {
            return conversationRepository.save(conversation);
        } catch (DataIntegrityViolationException ex) {
            return conversationRepository.findByConversationKey(conversation.getConversationKey()).orElseThrow(() -> ex);
        }
    }

    private ChatConversation requireAccessibleConversation(AuthenticatedUser user, Long conversationId) {
        ChatConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
        if (!canAccessConversation(user, conversation)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该会话");
        }
        return conversation;
    }

    private boolean canAccessConversation(AuthenticatedUser user, ChatConversation conversation) {
        if (Objects.equals(conversation.getBuyerUserId(), user.userId())
                || Objects.equals(conversation.getMerchantUserId(), user.userId())) {
            return true;
        }
        if (!isAdmin(user)) {
            return false;
        }
        if (Boolean.TRUE.equals(conversation.getAdminJoined()) && Objects.equals(conversation.getAdminUserId(), user.userId())) {
            return true;
        }
        return BIZ_AFTER_SALE.equalsIgnoreCase(conversation.getBizType())
                && AF_STATUS_ADMIN_REQUESTED.equalsIgnoreCase(asText(conversation.getAfterSaleStatus()));
    }

    private void updateConversationAfterMessage(ChatConversation conversation,
                                                RoleSide senderSide,
                                                MessageType messageType,
                                                String content,
                                                LocalDateTime createdAt) {
        conversation.setLastMessageType(messageType);
        conversation.setLastMessageText(truncate(content));
        conversation.setLastMessageAt(createdAt);

        if (senderSide == RoleSide.BUYER) {
            conversation.setMerchantUnreadCount(safeCount(conversation.getMerchantUnreadCount()) + 1);
            if (Boolean.TRUE.equals(conversation.getAdminJoined())) {
                conversation.setAdminUnreadCount(safeCount(conversation.getAdminUnreadCount()) + 1);
            }
            return;
        }
        if (senderSide == RoleSide.MERCHANT) {
            conversation.setBuyerUnreadCount(safeCount(conversation.getBuyerUnreadCount()) + 1);
            if (Boolean.TRUE.equals(conversation.getAdminJoined())) {
                conversation.setAdminUnreadCount(safeCount(conversation.getAdminUnreadCount()) + 1);
            }
            return;
        }
        if (senderSide == RoleSide.ADMIN) {
            conversation.setBuyerUnreadCount(safeCount(conversation.getBuyerUnreadCount()) + 1);
            conversation.setMerchantUnreadCount(safeCount(conversation.getMerchantUnreadCount()) + 1);
            conversation.setAdminUnreadCount(0);
        }
    }

    private void sendSystemMessage(ChatConversation conversation,
                                   AuthenticatedUser user,
                                   MessageType messageType,
                                   String content,
                                   Map<String, Object> payload) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversation.getId());
        message.setSenderUserId(user.userId());
        message.setSenderRole(resolveRoleSide(user, conversation).name());
        message.setSenderNickname(resolveNickname(user));
        message.setClientMsgId("sys-" + UUID.randomUUID());
        message.setMessageType(messageType);
        message.setContent(content);
        message.setPayloadJson(normalizePayloadJson(writePayload(payload)));
        ChatMessage saved = messageRepository.save(message);
        updateConversationAfterMessage(conversation, resolveRoleSide(user, conversation), messageType, content, saved.getCreatedAt());
    }

    private String handleAdminJoinAction(AuthenticatedUser user, ChatConversation conversation) {
        if (!isAdmin(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可执行该操作");
        }
        joinAdmin(conversation, user);
        sendSystemMessage(conversation, user, MessageType.SYSTEM_NOTICE, resolveNickname(user) + "管理员进入会话", Map.of("actionType", ACTION_ADMIN_JOIN));
        return asText(conversation.getAfterSaleStatus());
    }

    private void joinAdmin(ChatConversation conversation, AuthenticatedUser user) {
        conversation.setAdminJoined(true);
        conversation.setAdminUserId(user.userId());
        conversation.setAdminNickname(resolveNickname(user));
        conversation.setAdminUnreadCount(0);
    }

    private String requireBuyer(RoleSide side, String nextStatus) {
        if (side != RoleSide.BUYER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅买家可执行该操作");
        }
        return nextStatus;
    }

    private String requireMerchant(RoleSide side, String nextStatus) {
        if (side != RoleSide.MERCHANT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅商家可执行该操作");
        }
        return nextStatus;
    }

    private String requireAdmin(RoleSide side, String nextStatus) {
        if (side != RoleSide.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可执行该操作");
        }
        return nextStatus;
    }

    private String buildActionMessage(String actionType, String nickname, String remark) {
        String base = switch (actionType) {
            case ACTION_APPLY_RETURN -> nickname + "申请了退货";
            case ACTION_APPLY_EXCHANGE -> nickname + "申请了换货";
            case ACTION_REQUEST_ADMIN_INTERVENTION -> nickname + "申请管理员介入";
            case ACTION_MERCHANT_APPROVE_RETURN -> nickname + "同意了退货申请";
            case ACTION_MERCHANT_APPROVE_EXCHANGE -> nickname + "同意了换货申请";
            case ACTION_MERCHANT_REJECT -> nickname + "拒绝了当前售后申请";
            case ACTION_ADMIN_FORCE_CANCEL -> nickname + "强制取消了售后";
            case ACTION_ADMIN_FORCE_REFUND -> nickname + "强制退款完成";
            default -> nickname + "执行了售后操作";
        };
        if (!StringUtils.hasText(remark)) {
            return base;
        }
        return base + "（备注：" + remark.trim() + "）";
    }

    private Map<String, Object> pickOrderItem(List<Map<String, Object>> items, Long storeId) {
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单中没有可售后商品");
        }
        if (storeId == null || storeId <= 0) {
            return items.get(0);
        }
        return items.stream()
                .filter(item -> Objects.equals(toLong(item.get("storeId")), storeId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "指定店铺不在该订单中"));
    }

    private Map<String, Object> requireStore(Long storeId) {
        Map<String, Object> store = merchantCatalogClient.publicStoreDetail(storeId);
        if (store == null || store.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "店铺不存在");
        }
        return store;
    }

    private Map<String, Object> requireProduct(Long productId) {
        Map<String, Object> product = merchantCatalogClient.publicProductDetail(productId);
        if (product == null || product.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        return product;
    }

    private Map<String, Object> toConversationView(ChatConversation conversation, AuthenticatedUser user) {
        RoleSide side = resolveRoleSide(user, conversation);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", conversation.getId());
        result.put("conversationKey", conversation.getConversationKey());
        result.put("bizType", conversation.getBizType());
        result.put("orderNo", conversation.getOrderNo());
        result.put("afterSaleStatus", conversation.getAfterSaleStatus());
        result.put("storeId", conversation.getStoreId());
        result.put("storeTitle", conversation.getStoreTitle());
        result.put("storeImageUrl", conversation.getStoreImageUrl());
        result.put("buyerUserId", conversation.getBuyerUserId());
        result.put("buyerUsername", conversation.getBuyerUsername());
        result.put("buyerNickname", conversation.getBuyerNickname());
        result.put("merchantUserId", conversation.getMerchantUserId());
        result.put("merchantUsername", conversation.getMerchantUsername());
        result.put("merchantNickname", conversation.getMerchantNickname());
        result.put("adminJoined", conversation.getAdminJoined());
        result.put("adminUserId", conversation.getAdminUserId());
        result.put("adminNickname", conversation.getAdminNickname());
        result.put("sourceType", conversation.getSourceType());
        result.put("productId", conversation.getProductId());
        result.put("productTitle", conversation.getProductTitle());
        result.put("productImageUrl", conversation.getProductImageUrl());
        result.put("productDescription", conversation.getProductDescription());
        result.put("productPrice", conversation.getProductPrice());
        result.put("lastMessageType", conversation.getLastMessageType());
        result.put("lastMessageText", conversation.getLastMessageText());
        result.put("lastMessageAt", conversation.getLastMessageAt());
        result.put("buyerUnreadCount", conversation.getBuyerUnreadCount());
        result.put("merchantUnreadCount", conversation.getMerchantUnreadCount());
        result.put("adminUnreadCount", conversation.getAdminUnreadCount());
        result.put("status", conversation.getStatus());
        result.put("roleSide", side.name());
        result.put("counterpartName", counterpartName(side, conversation));
        result.put("mineUnreadCount", switch (side) {
            case BUYER -> conversation.getBuyerUnreadCount();
            case MERCHANT -> conversation.getMerchantUnreadCount();
            case ADMIN -> conversation.getAdminUnreadCount();
            case UNKNOWN -> 0;
        });
        result.put("availableActions", availableActions(side, conversation));
        return result;
    }

    private List<String> availableActions(RoleSide side, ChatConversation conversation) {
        if (!BIZ_AFTER_SALE.equalsIgnoreCase(conversation.getBizType())) {
            return List.of();
        }
        if (side == RoleSide.BUYER) {
            return List.of(ACTION_APPLY_RETURN, ACTION_APPLY_EXCHANGE, ACTION_REQUEST_ADMIN_INTERVENTION);
        }
        if (side == RoleSide.MERCHANT) {
            return List.of(ACTION_MERCHANT_APPROVE_RETURN, ACTION_MERCHANT_APPROVE_EXCHANGE, ACTION_MERCHANT_REJECT);
        }
        if (side == RoleSide.ADMIN) {
            if (Boolean.TRUE.equals(conversation.getAdminJoined())) {
                return List.of(ACTION_ADMIN_FORCE_CANCEL, ACTION_ADMIN_FORCE_REFUND);
            }
            return List.of(ACTION_ADMIN_JOIN);
        }
        if (AF_STATUS_ADMIN_REQUESTED.equalsIgnoreCase(asText(conversation.getAfterSaleStatus()))) {
            return List.of(ACTION_ADMIN_JOIN);
        }
        return List.of();
    }

    private String counterpartName(RoleSide side, ChatConversation conversation) {
        return switch (side) {
            case BUYER -> conversation.getMerchantNickname();
            case MERCHANT -> conversation.getBuyerNickname();
            case ADMIN -> conversation.getStoreTitle();
            case UNKNOWN -> conversation.getStoreTitle();
        };
    }

    private Map<String, Object> toMessageView(ChatMessage message, AuthenticatedUser user, ChatConversation conversation) {
        RoleSide side = resolveRoleSide(user, conversation);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", message.getId());
        result.put("conversationId", message.getConversationId());
        result.put("senderUserId", message.getSenderUserId());
        result.put("senderRole", message.getSenderRole());
        result.put("senderNickname", message.getSenderNickname());
        result.put("clientMsgId", message.getClientMsgId());
        result.put("messageType", message.getMessageType());
        result.put("content", message.getContent());
        result.put("payload", parsePayload(message.getPayloadJson()));
        result.put("createdAt", message.getCreatedAt());
        result.put("mine", Objects.equals(message.getSenderUserId(), user.userId()));
        result.put("viewerRoleSide", side.name());
        return result;
    }

    private String shopConversationKey(Long storeId, Long userId) {
        return storeId + ":" + userId;
    }

    private String afterSaleConversationKey(String orderNo, Long storeId) {
        return "AFTER_SALE:" + orderNo + ":" + storeId;
    }

    private MessageType parseMessageType(String value) {
        try {
            return MessageType.valueOf(value.trim().toUpperCase());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的消息类型");
        }
    }

    private RoleSide resolveRoleSide(AuthenticatedUser user, ChatConversation conversation) {
        if (Objects.equals(conversation.getBuyerUserId(), user.userId())) {
            return RoleSide.BUYER;
        }
        if (Objects.equals(conversation.getMerchantUserId(), user.userId())) {
            return RoleSide.MERCHANT;
        }
        if (isAdmin(user) && Boolean.TRUE.equals(conversation.getAdminJoined()) && Objects.equals(conversation.getAdminUserId(), user.userId())) {
            return RoleSide.ADMIN;
        }
        if (isAdmin(user) && BIZ_AFTER_SALE.equalsIgnoreCase(conversation.getBizType())
                && AF_STATUS_ADMIN_REQUESTED.equalsIgnoreCase(asText(conversation.getAfterSaleStatus()))) {
            return RoleSide.ADMIN;
        }
        return RoleSide.UNKNOWN;
    }

    private boolean isAdmin(AuthenticatedUser user) {
        return user != null && user.role() == UserRole.ADMIN;
    }

    private String resolveNickname(AuthenticatedUser user) {
        return StringUtils.hasText(user.nickname()) ? user.nickname().trim() : user.username();
    }

    private String resolveMessageContent(MessageType messageType, String content, String payloadJson) {
        if (messageType == MessageType.PRODUCT_CARD) {
            Map<String, Object> payload = parsePayload(payloadJson);
            String title = asText(payload.get("productTitle"));
            String storeTitle = asText(payload.get("storeTitle"));
            return StringUtils.hasText(title) ? title : (StringUtils.hasText(storeTitle) ? storeTitle : "商品卡片");
        }
        return content;
    }

    private String normalizePayloadJson(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return null;
        }
        try {
            Object value = objectMapper.readValue(payloadJson, Object.class);
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return payloadJson;
        }
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of("raw", payloadJson);
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String normalizeSourceType(String sourceType) {
        return StringUtils.hasText(sourceType) ? sourceType.trim() : "store-detail";
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        int max = 500;
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private int safeCount(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    public enum RoleSide {
        BUYER,
        MERCHANT,
        ADMIN,
        UNKNOWN
    }

    public record SendMessageRequest(String clientMsgId, String messageType, String content, String payloadJson) {
    }

    public record AfterSaleActionRequest(String actionType, String remark) {
    }
}
