package org.example.order.controller;

import jakarta.validation.Valid;
import org.example.order.dto.CreateOrderRequest;
import org.example.order.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    public ResponseEntity<?> create(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.ok(orderService.createOrder(request));
    }

    @GetMapping("/orders/{orderNo}")
    public ResponseEntity<?> detail(@PathVariable String orderNo) {
        return ResponseEntity.ok(orderService.getOrder(orderNo));
    }

    @GetMapping("/orders")
    public ResponseEntity<?> query(@RequestParam(required = false) Long userId,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(defaultValue = "20") Integer limit) {
        return ResponseEntity.ok(orderService.queryComplex(userId, status, limit));
    }

    @GetMapping("/orders/notification-summary")
    public ResponseEntity<?> userNotificationSummary(@RequestParam Long userId) {
        return ResponseEntity.ok(orderService.userNotificationSummary(userId));
    }

    @PostMapping("/internal/orders/{orderNo}/paid")
    public ResponseEntity<?> markPaid(@PathVariable String orderNo) {
        return ResponseEntity.ok(orderService.markPaid(orderNo));
    }

    @PostMapping("/orders/{orderNo}/close")
    public ResponseEntity<?> close(@PathVariable String orderNo) {
        return ResponseEntity.ok(orderService.closeOrder(orderNo));
    }

    @GetMapping("/merchant/orders")
    public ResponseEntity<?> merchantOrders(@RequestParam Long storeId,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(defaultValue = "30") Integer limit) {
        return ResponseEntity.ok(orderService.queryMerchantOrders(storeId, status, limit));
    }

    @GetMapping("/merchant/orders/stats")
    public ResponseEntity<?> merchantStats(@RequestParam Long storeId) {
        return ResponseEntity.ok(orderService.merchantStats(storeId));
    }

    @PostMapping("/merchant/orders/{orderNo}/ship")
    public ResponseEntity<?> ship(@PathVariable String orderNo,
                                  @RequestParam Long storeId) {
        return ResponseEntity.ok(orderService.shipOrder(storeId, orderNo));
    }

    @PostMapping("/orders/{orderNo}/confirm-receipt")
    public ResponseEntity<?> confirmReceipt(@PathVariable String orderNo) {
        return ResponseEntity.ok(orderService.confirmReceipt(orderNo));
    }

    @PostMapping("/orders/{orderNo}/after-sale")
    public ResponseEntity<?> afterSale(@PathVariable String orderNo) {
        return ResponseEntity.ok(orderService.requestAfterSale(orderNo));
    }

    @PostMapping("/internal/orders/{orderNo}/after-sale/force-cancel")
    public ResponseEntity<?> forceCancelAfterSale(@PathVariable String orderNo) {
        return ResponseEntity.ok(orderService.forceCancelAfterSale(orderNo));
    }

    @PostMapping("/internal/orders/{orderNo}/after-sale/force-refund")
    public ResponseEntity<?> forceRefundAfterSale(@PathVariable String orderNo) {
        return ResponseEntity.ok(orderService.forceRefundAfterSale(orderNo));
    }
}
