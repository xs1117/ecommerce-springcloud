package org.example.order.service;

import org.example.order.domain.OrderDetailView;
import org.example.order.domain.OrderInfo;
import org.example.order.domain.OrderItem;
import org.example.order.domain.OrderStatus;
import org.example.order.dto.CreateOrderRequest;
import org.example.order.integration.IntegrationEndpointResolver;
import org.example.order.mapper.OrderShardMapper;
import org.example.order.mq.InventoryReservedEvent;
import org.example.order.mq.OrderCreatedEvent;
import org.example.order.mq.OrderEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final DateTimeFormatter ORDER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final OrderShardMapper orderShardMapper;
    private final OrderEventPublisher eventPublisher;
    private final IntegrationEndpointResolver endpointResolver;
    private final RestTemplate restTemplate;
    private final String inventoryServiceId;
    private final String merchantServiceId;
    private final String userServiceId;
    private final String inventoryBaseUrl;
    private final String merchantBaseUrl;
    private final String userBaseUrl;
    private final int payTimeoutMinutes;

    public OrderService(OrderShardMapper orderShardMapper,
                        OrderEventPublisher eventPublisher,
                        IntegrationEndpointResolver endpointResolver,
                        @Value("${app.integration.inventory.service-id:ecommerce-inventory-service}") String inventoryServiceId,
                        @Value("${app.integration.merchant.service-id:ecommerce-merchant-service}") String merchantServiceId,
                        @Value("${app.integration.user.service-id:ecommerce-user-service}") String userServiceId,
                        @Value("${app.integration.inventory.base-url:http://localhost:8086}") String inventoryBaseUrl,
                        @Value("${app.integration.merchant.base-url:http://localhost:8084}") String merchantBaseUrl,
                        @Value("${app.integration.user.base-url:http://localhost:8083}") String userBaseUrl,
                        @Value("${app.order.pay-timeout-minutes:10}") int payTimeoutMinutes) {
        this.orderShardMapper = orderShardMapper;
        this.eventPublisher = eventPublisher;
        this.endpointResolver = endpointResolver;
        this.restTemplate = new RestTemplate();
        this.inventoryServiceId = inventoryServiceId;
        this.merchantServiceId = merchantServiceId;
        this.userServiceId = userServiceId;
        this.inventoryBaseUrl = inventoryBaseUrl;
        this.merchantBaseUrl = merchantBaseUrl;
        this.userBaseUrl = userBaseUrl;
        this.payTimeoutMinutes = Math.max(1, payTimeoutMinutes);
    }

    @Transactional
    public Map<String, Object> createOrder(CreateOrderRequest request) {
        String orderNo = generateOrderNo(request.userId());
        int shard = shardByUserId(request.userId());
        String orderTable = "order_info_" + shard;
        String itemTable = "order_item_" + shard;

        BigDecimal total = request.items().stream()
                .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CouponApplyResult couponApplyResult = applyCouponForOrder(
                request.userId(),
                request.couponId(),
                orderNo,
                total
        );
        BigDecimal payAmount = couponApplyResult.finalAmount();

        OrderInfo order = new OrderInfo();
        order.setOrderNo(orderNo);
        order.setUserId(request.userId());
        order.setTotalAmount(total);
        order.setPayAmount(payAmount);
        order.setStatus(OrderStatus.CREATED.name());
        orderShardMapper.insertOrder(orderTable, order);

        List<OrderCreatedEvent.OrderSkuItem> skuItems = new ArrayList<>();
        for (CreateOrderRequest.CreateOrderItem createItem : request.items()) {
            OrderItem item = new OrderItem();
            item.setOrderNo(orderNo);
            item.setProductId(createItem.productId());
            item.setProductName(createItem.productName());
            item.setStoreId(createItem.storeId());
            item.setStoreName(createItem.storeName());
            item.setProductImageUrl(createItem.productImageUrl());
            item.setProductDescription(createItem.productDescription());
            item.setUnitPrice(createItem.unitPrice());
            item.setQuantity(createItem.quantity());
            orderShardMapper.insertOrderItem(itemTable, item);
            skuItems.add(new OrderCreatedEvent.OrderSkuItem(item.getProductId(), item.getQuantity()));
        }

        eventPublisher.publishOrderCreated(new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                orderNo,
                request.userId(),
                total,
                skuItems
        ));

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("orderNo", orderNo);
        result.put("status", order.getStatus());
        result.put("totalAmount", total);
        result.put("payAmount", payAmount);
        result.put("couponApplied", couponApplyResult.couponApplied());
        result.put("discountAmount", couponApplyResult.discountAmount());
        result.put("couponId", request.couponId());
        return result;
    }

    public Map<String, Object> getOrder(String orderNo) {
        int shard = shardByOrderNo(orderNo);
        String orderTable = "order_info_" + shard;
        String itemTable = "order_item_" + shard;
        OrderInfo info = orderShardMapper.findByOrderNo(orderTable, orderNo);
        if (info == null) {
            throw new IllegalArgumentException("order not found");
        }
        List<OrderItem> items = orderShardMapper.findItemsByOrderNo(itemTable, orderNo);
        return Map.of("order", info, "items", items);
    }

    public List<OrderDetailView> queryComplex(Long userId, String status, Integer limit) {
        int finalLimit = (limit == null || limit < 1) ? 20 : Math.min(limit, 100);
        return orderShardMapper.queryComplex(userId, status, finalLimit);
    }

    public List<Map<String, Object>> queryMerchantOrders(Long storeId, String status, Integer limit) {
        if (storeId == null) {
            throw new IllegalArgumentException("storeId is required");
        }
        int finalLimit = (limit == null || limit < 1) ? 30 : Math.min(limit, 100);
        return orderShardMapper.queryMerchantOrders(storeId, status, finalLimit);
    }

    public Map<String, Object> merchantStats(Long storeId) {
        if (storeId == null) {
            throw new IllegalArgumentException("storeId is required");
        }
        Map<String, Object> stats = orderShardMapper.queryMerchantStats(storeId);
        if (stats == null) {
            return Map.of("totalOrders", 0, "pendingOrders", 0, "totalRevenue", BigDecimal.ZERO, "latestOrderUpdatedAt", "");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalOrders", intValue(stats.get("totalOrders")));
        result.put("pendingOrders", intValue(stats.get("pendingOrders")));
        result.put("totalRevenue", decimalValue(stats.get("totalRevenue")));
        result.put("latestOrderUpdatedAt", Objects.toString(stats.get("latestOrderUpdatedAt"), ""));
        return result;
    }

    public Map<String, Object> userNotificationSummary(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        Map<String, Object> stats = orderShardMapper.queryUserNotificationSummary(userId);
        if (stats == null) {
            return Map.of("pendingCount", 0, "latestUpdatedAt", "");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pendingCount", intValue(stats.get("pendingCount")));
        result.put("latestUpdatedAt", Objects.toString(stats.get("latestUpdatedAt"), ""));
        return result;
    }

    @Transactional
    public void handleInventoryReserved(InventoryReservedEvent event) {
        if (event == null || event.orderNo() == null || event.orderNo().isBlank()) {
            return;
        }
        String next = event.success() ? OrderStatus.WAIT_PAY.name() : OrderStatus.STOCK_FAILED.name();
        int shard = shardByOrderNo(event.orderNo());
        String orderTable = "order_info_" + shard;
        int updated = orderShardMapper.updateOrderStatusIfCurrent(orderTable, event.orderNo(), next, OrderStatus.CREATED.name());
        if (!event.success() && updated > 0) {
            eventPublisher.publishOrderClosed(UUID.randomUUID().toString(), event.orderNo());
        }
    }

    @Transactional
    public Map<String, Object> markPaid(String orderNo) {
        int shard = shardByOrderNo(orderNo);
        String orderTable = "order_info_" + shard;
        String itemTable = "order_item_" + shard;
        OrderInfo info = orderShardMapper.findByOrderNo(orderTable, orderNo);
        if (info == null) {
            return Map.of("ok", false, "accepted", false, "reason", "ORDER_NOT_FOUND");
        }
        if (OrderStatus.TO_SHIP.name().equals(info.getStatus())
                || OrderStatus.TO_RECEIVE.name().equals(info.getStatus())
                || OrderStatus.FINISHED.name().equals(info.getStatus())) {
            return Map.of("ok", true, "accepted", true, "alreadyPaid", true, "status", info.getStatus());
        }
        if (!OrderStatus.WAIT_PAY.name().equals(info.getStatus())) {
            return Map.of("ok", true, "accepted", false, "reason", "ORDER_NOT_PAYABLE", "status", info.getStatus());
        }
        int updated = orderShardMapper.updateOrderStatusIfCurrent(
                orderTable,
                orderNo,
                OrderStatus.TO_SHIP.name(),
                OrderStatus.WAIT_PAY.name()
        );
        if (updated <= 0) {
            OrderInfo latest = orderShardMapper.findByOrderNo(orderTable, orderNo);
            String latestStatus = latest == null ? "UNKNOWN" : latest.getStatus();
            boolean alreadyPaid = OrderStatus.TO_SHIP.name().equals(latestStatus)
                    || OrderStatus.TO_RECEIVE.name().equals(latestStatus)
                    || OrderStatus.FINISHED.name().equals(latestStatus);
            return Map.of(
                    "ok", true,
                    "accepted", alreadyPaid,
                    "alreadyPaid", alreadyPaid,
                    "reason", alreadyPaid ? "" : "ORDER_NOT_PAYABLE",
                    "status", latestStatus
            );
        }

        List<OrderItem> items = orderShardMapper.findItemsByOrderNo(itemTable, orderNo);
        List<OrderCreatedEvent.OrderSkuItem> paidItems = items.stream()
                .map(item -> new OrderCreatedEvent.OrderSkuItem(item.getProductId(), item.getQuantity()))
                .toList();
        syncInventoryConfirm(orderNo, paidItems);
        syncMerchantPaid(orderNo, paidItems);
        syncUserPointsEarn(info.getUserId(), info.getPayAmount(), orderNo);
        eventPublisher.publishOrderPaid(UUID.randomUUID().toString(), orderNo);
        return Map.of("ok", true, "accepted", true, "alreadyPaid", false, "status", OrderStatus.TO_SHIP.name());
    }

    @Transactional
    public Map<String, Object> closeOrder(String orderNo) {
        int shard = shardByOrderNo(orderNo);
        String orderTable = "order_info_" + shard;
        OrderInfo info = orderShardMapper.findByOrderNo(orderTable, orderNo);
        if (info == null) {
            throw new IllegalArgumentException("order not found");
        }
        if (OrderStatus.CLOSED.name().equals(info.getStatus())) {
            return Map.of("ok", true, "closed", true, "alreadyClosed", true, "status", info.getStatus());
        }
        if (OrderStatus.TO_SHIP.name().equals(info.getStatus())
                || OrderStatus.TO_RECEIVE.name().equals(info.getStatus())
                || OrderStatus.FINISHED.name().equals(info.getStatus())) {
            throw new IllegalArgumentException("paid order cannot be closed");
        }
        int updated = orderShardMapper.updateOrderStatusIfCurrent(
                orderTable,
                orderNo,
                OrderStatus.CLOSED.name(),
                OrderStatus.WAIT_PAY.name()
        );
        if (updated <= 0) {
            OrderInfo latest = orderShardMapper.findByOrderNo(orderTable, orderNo);
            throw new IllegalArgumentException("order status is " + (latest == null ? "UNKNOWN" : latest.getStatus()) + ", cannot close");
        }
        eventPublisher.publishOrderClosed(UUID.randomUUID().toString(), orderNo);
        return Map.of("ok", true, "closed", true, "alreadyClosed", false, "status", OrderStatus.CLOSED.name());
    }

    @Transactional
    public Map<String, Object> shipOrder(Long storeId, String orderNo) {
        if (storeId == null) {
            throw new IllegalArgumentException("storeId is required");
        }
        int shard = shardByOrderNo(orderNo);
        String orderTable = "order_info_" + shard;
        String itemTable = "order_item_" + shard;
        OrderInfo info = orderShardMapper.findByOrderNo(orderTable, orderNo);
        if (info == null) {
            throw new IllegalArgumentException("order not found");
        }
        List<OrderItem> items = orderShardMapper.findItemsByOrderNo(itemTable, orderNo);
        boolean owned = items.stream().anyMatch(item -> storeId.equals(item.getStoreId()));
        if (!owned) {
            throw new IllegalArgumentException("order does not belong to this store");
        }
        int updated = orderShardMapper.updateOrderStatusIfCurrent(orderTable, orderNo, OrderStatus.TO_RECEIVE.name(), OrderStatus.TO_SHIP.name());
        if (updated <= 0) {
            throw new IllegalArgumentException("order status is " + info.getStatus() + ", cannot ship");
        }
        return Map.of("ok", true, "status", OrderStatus.TO_RECEIVE.name());
    }

    @Transactional
    public Map<String, Object> confirmReceipt(String orderNo) {
        int shard = shardByOrderNo(orderNo);
        String orderTable = "order_info_" + shard;
        int updated = orderShardMapper.updateOrderStatusIfCurrent(orderTable, orderNo, OrderStatus.FINISHED.name(), OrderStatus.TO_RECEIVE.name());
        if (updated <= 0) {
            OrderInfo latest = orderShardMapper.findByOrderNo(orderTable, orderNo);
            throw new IllegalArgumentException("order status is " + (latest == null ? "UNKNOWN" : latest.getStatus()) + ", cannot confirm receipt");
        }
        return Map.of("ok", true, "status", OrderStatus.FINISHED.name());
    }

    @Transactional
    public Map<String, Object> requestAfterSale(String orderNo) {
        int shard = shardByOrderNo(orderNo);
        String orderTable = "order_info_" + shard;
        int updated = orderShardMapper.updateOrderStatusIfCurrent(orderTable, orderNo, OrderStatus.AFTER_SALE.name(), OrderStatus.TO_RECEIVE.name());
        if (updated <= 0) {
            OrderInfo latest = orderShardMapper.findByOrderNo(orderTable, orderNo);
            throw new IllegalArgumentException("order status is " + (latest == null ? "UNKNOWN" : latest.getStatus()) + ", cannot apply after-sale");
        }
        return Map.of("ok", true, "status", OrderStatus.AFTER_SALE.name());
    }

    @Transactional
    public Map<String, Object> forceCancelAfterSale(String orderNo) {
        int shard = shardByOrderNo(orderNo);
        String orderTable = "order_info_" + shard;
        int updated = orderShardMapper.updateOrderStatusIfCurrent(orderTable, orderNo, OrderStatus.TO_RECEIVE.name(), OrderStatus.AFTER_SALE.name());
        if (updated <= 0) {
            OrderInfo latest = orderShardMapper.findByOrderNo(orderTable, orderNo);
            throw new IllegalArgumentException("order status is " + (latest == null ? "UNKNOWN" : latest.getStatus()) + ", cannot force-cancel after-sale");
        }
        return Map.of("ok", true, "status", OrderStatus.TO_RECEIVE.name());
    }

    @Transactional
    public Map<String, Object> forceRefundAfterSale(String orderNo) {
        int shard = shardByOrderNo(orderNo);
        String orderTable = "order_info_" + shard;
        int updated = orderShardMapper.updateOrderStatusIfCurrent(orderTable, orderNo, OrderStatus.CLOSED.name(), OrderStatus.AFTER_SALE.name());
        if (updated <= 0) {
            OrderInfo latest = orderShardMapper.findByOrderNo(orderTable, orderNo);
            throw new IllegalArgumentException("order status is " + (latest == null ? "UNKNOWN" : latest.getStatus()) + ", cannot force-refund after-sale");
        }
        return Map.of("ok", true, "status", OrderStatus.CLOSED.name());
    }

    private void syncInventoryConfirm(String orderNo, List<OrderCreatedEvent.OrderSkuItem> items) {
        String targetBaseUrl;
        try {
            targetBaseUrl = endpointResolver.resolveBaseUrl(inventoryServiceId, inventoryBaseUrl);
        } catch (Exception ignored) {
            return;
        }
        for (OrderCreatedEvent.OrderSkuItem item : items) {
            try {
                String reservationNo = orderNo + "-" + item.productId();
                restTemplate.postForEntity(
                        targetBaseUrl + "/api/inventory/confirm",
                        Map.of("reservationNo", reservationNo),
                        Map.class
                );
            } catch (HttpClientErrorException ex) {
                String body = ex.getResponseBodyAsString();
                if (ex.getStatusCode() == HttpStatus.BAD_REQUEST && body.contains("reservation not found")) {
                    log.info("legacy order inventory confirm skipped, orderNo={}, productId={}", orderNo, item.productId());
                    continue;
                }
                log.warn("inventory confirm sync failed, orderNo={}, productId={}", orderNo, item.productId(), ex);
            } catch (Exception ex) {
                log.warn("inventory confirm sync failed, orderNo={}, productId={}", orderNo, item.productId(), ex);
            }
        }
    }

    private void syncMerchantPaid(String orderNo, List<OrderCreatedEvent.OrderSkuItem> items) {
        try {
            String targetBaseUrl = endpointResolver.resolveBaseUrl(merchantServiceId, merchantBaseUrl);
            ResponseEntity<?> response = restTemplate.postForEntity(
                    targetBaseUrl + "/api/merchant/internal/orders/paid",
                    Map.of(
                            "orderNo", orderNo,
                            "items", items.stream().map(item -> Map.of(
                                    "productId", item.productId(),
                                    "quantity", item.quantity()
                            )).toList()
                    ),
                    Object.class
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("merchant sync failed");
            }
        } catch (Exception ex) {
            log.warn("merchant paid sync failed, orderNo={}", orderNo, ex);
        }
    }

    private CouponApplyResult applyCouponForOrder(Long userId, Long couponId, String orderNo, BigDecimal totalAmount) {
        if (couponId == null || couponId <= 0) {
            return new CouponApplyResult(false, BigDecimal.ZERO, totalAmount);
        }
        try {
            String targetBaseUrl = endpointResolver.resolveBaseUrl(userServiceId, userBaseUrl);
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(
                    targetBaseUrl + "/api/user/coupons/internal/use-for-order",
                    Map.of(
                            "userId", userId,
                            "couponId", couponId,
                            "orderNo", orderNo,
                            "orderAmount", totalAmount
                    ),
                    (Class<Map<String, Object>>) (Class<?>) Map.class
            );
            Map<String, Object> body = response.getBody();
            if (body == null || !Boolean.TRUE.equals(body.get("ok"))) {
                throw new IllegalArgumentException("coupon settlement failed");
            }
            BigDecimal discountAmount = decimalValue(body.get("discountAmount"));
            BigDecimal finalAmount = decimalValue(body.get("finalAmount"));
            return new CouponApplyResult(Boolean.TRUE.equals(body.get("couponApplied")), discountAmount, finalAmount);
        } catch (Exception ex) {
            throw new IllegalArgumentException("优惠券不可用或结算失败");
        }
    }

    private void syncUserPointsEarn(Long userId, BigDecimal payAmount, String orderNo) {
        if (userId == null || payAmount == null || payAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        int delta = payAmount.setScale(0, java.math.RoundingMode.DOWN).intValue();
        if (delta <= 0) {
            return;
        }
        try {
            String targetBaseUrl = endpointResolver.resolveBaseUrl(userServiceId, userBaseUrl);
            restTemplate.postForEntity(
                    targetBaseUrl + "/api/user/coupons/internal/points/earn-order",
                    Map.of(
                            "userId", userId,
                            "delta", delta,
                            "reason", "订单消费赠送积分:" + orderNo
                    ),
                    Object.class
            );
        } catch (Exception ex) {
            log.warn("user points sync failed, userId={}, orderNo={}", userId, orderNo, ex);
        }
    }

    @Async("orderExecutor")
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void closeTimeoutOrders() {
        for (int shard = 0; shard < 2; shard++) {
            String table = "order_info_" + shard;
            List<OrderInfo> timeoutOrders = orderShardMapper.findTimeoutOrders(table, payTimeoutMinutes);
            for (OrderInfo order : timeoutOrders) {
                int updated = orderShardMapper.updateOrderStatusIfCurrent(
                        table,
                        order.getOrderNo(),
                        OrderStatus.CLOSED.name(),
                        OrderStatus.WAIT_PAY.name()
                );
                if (updated > 0) {
                    eventPublisher.publishOrderClosed(UUID.randomUUID().toString(), order.getOrderNo());
                }
            }
        }
    }


    private String generateOrderNo(Long userId) {
        return "ORD" + LocalDateTime.now().format(ORDER_NO_FMT) + (userId % 1000);
    }

    private int shardByUserId(Long userId) {
        return Math.floorMod(userId.intValue(), 2);
    }

    private int shardByOrderNo(String orderNo) {
        char c = orderNo.charAt(orderNo.length() - 1);
        return Character.isDigit(c) ? (c - '0') % 2 : 0;
    }

    private int intValue(Object value) {
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private record CouponApplyResult(boolean couponApplied, BigDecimal discountAmount, BigDecimal finalAmount) {
    }
}

