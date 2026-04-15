package org.example.payment.service;

import org.example.payment.domain.PaymentOrder;
import org.example.payment.domain.PaymentStatus;
import org.example.payment.dto.CreatePaymentRequest;
import org.example.payment.dto.MockSuccessRequest;
import org.example.payment.dto.RefundRequest;
import org.example.payment.integration.IntegrationEndpointResolver;
import org.example.payment.mq.PaymentEventPublisher;
import org.example.payment.mq.PaymentSuccessEvent;
import org.example.payment.repository.PaymentOrderRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    private static final DateTimeFormatter PAYMENT_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentEventPublisher paymentEventPublisher;
    private final Executor paymentExecutor;
    private final IntegrationEndpointResolver endpointResolver;
    private final RestTemplate restTemplate;
    private final String orderServiceId;
    private final String orderBaseUrl;

    public PaymentService(PaymentOrderRepository paymentOrderRepository,
                          PaymentEventPublisher paymentEventPublisher,
                          @Qualifier("paymentExecutor") Executor paymentExecutor,
                          IntegrationEndpointResolver endpointResolver,
                          @Value("${app.integration.order.service-id:ecommerce-order-service}") String orderServiceId,
                          @Value("${app.integration.order.base-url:http://localhost:8087}") String orderBaseUrl) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.paymentEventPublisher = paymentEventPublisher;
        this.paymentExecutor = paymentExecutor;
        this.endpointResolver = endpointResolver;
        this.restTemplate = new RestTemplate();
        this.orderServiceId = orderServiceId;
        this.orderBaseUrl = orderBaseUrl;
    }

    @Transactional
    public Map<String, Object> createPayment(CreatePaymentRequest request) {
        PaymentOrder existing = paymentOrderRepository.findByOrderNo(request.orderNo()).orElse(null);
        if (existing != null) {
            return Map.of("ok", true, "paymentNo", existing.getPaymentNo(), "status", existing.getStatus().name());
        }

        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setPaymentNo(generatePaymentNo());
        paymentOrder.setOrderNo(request.orderNo());
        paymentOrder.setAmount(request.amount());
        paymentOrder.setStatus(PaymentStatus.INIT);
        paymentOrder.setCreatedAt(LocalDateTime.now());
        paymentOrder.setUpdatedAt(LocalDateTime.now());
        paymentOrderRepository.save(paymentOrder);
        return Map.of("ok", true, "paymentNo", paymentOrder.getPaymentNo(), "status", paymentOrder.getStatus().name());
    }

    @Transactional
    public Map<String, Object> mockSuccess(MockSuccessRequest request) {
        PaymentOrder paymentOrder = paymentOrderRepository.findByPaymentNo(request.paymentNo())
                .orElseThrow(() -> new IllegalArgumentException("payment not found"));
        if (paymentOrder.getStatus() == PaymentStatus.SUCCESS) {
            return Map.of("ok", true, "paymentNo", paymentOrder.getPaymentNo(), "status", paymentOrder.getStatus().name());
        }
        OrderPaidSyncResult syncResult = notifyOrderPaidSync(paymentOrder.getOrderNo());
        if (!syncResult.accepted()) {
            throw new IllegalArgumentException("order is not payable: " + syncResult.reason());
        }
        paymentOrder.setStatus(PaymentStatus.SUCCESS);
        paymentOrder.setUpdatedAt(LocalDateTime.now());
        paymentOrderRepository.save(paymentOrder);
        CompletableFuture.runAsync(() -> publishSuccess(paymentOrder), paymentExecutor);
        return Map.of("ok", true, "paymentNo", paymentOrder.getPaymentNo(), "status", paymentOrder.getStatus().name());
    }

    @Transactional
    public Map<String, Object> refund(RefundRequest request) {
        PaymentOrder paymentOrder = paymentOrderRepository.findByPaymentNo(request.paymentNo())
                .orElseThrow(() -> new IllegalArgumentException("payment not found"));
        paymentOrder.setStatus(PaymentStatus.REFUNDED);
        paymentOrder.setUpdatedAt(LocalDateTime.now());
        paymentOrderRepository.save(paymentOrder);
        return Map.of("ok", true, "paymentNo", paymentOrder.getPaymentNo(), "status", paymentOrder.getStatus().name());
    }

    @Transactional
    public Map<String, Object> refundByOrderNo(String orderNo) {
        PaymentOrder paymentOrder = paymentOrderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("payment not found by orderNo"));
        paymentOrder.setStatus(PaymentStatus.REFUNDED);
        paymentOrder.setUpdatedAt(LocalDateTime.now());
        paymentOrderRepository.save(paymentOrder);
        return Map.of(
                "ok", true,
                "paymentNo", paymentOrder.getPaymentNo(),
                "orderNo", paymentOrder.getOrderNo(),
                "status", paymentOrder.getStatus().name()
        );
    }

    public Map<String, Object> query(String paymentNo) {
        PaymentOrder paymentOrder = paymentOrderRepository.findByPaymentNo(paymentNo)
                .orElseThrow(() -> new IllegalArgumentException("payment not found"));
        return Map.of(
                "paymentNo", paymentOrder.getPaymentNo(),
                "orderNo", paymentOrder.getOrderNo(),
                "amount", paymentOrder.getAmount(),
                "status", paymentOrder.getStatus(),
                "updatedAt", paymentOrder.getUpdatedAt()
        );
    }

    private void publishSuccess(PaymentOrder paymentOrder) {
        paymentEventPublisher.publishSuccess(new PaymentSuccessEvent(
                UUID.randomUUID().toString(),
                paymentOrder.getOrderNo(),
                paymentOrder.getPaymentNo(),
                paymentOrder.getAmount()
        ));
    }

    private OrderPaidSyncResult notifyOrderPaidSync(String orderNo) {
        try {
            String targetBaseUrl = endpointResolver.resolveBaseUrl(orderServiceId, orderBaseUrl);
            ResponseEntity<?> response = restTemplate.postForEntity(
                    targetBaseUrl + "/api/order/internal/orders/" + orderNo + "/paid",
                    null,
                    Object.class
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("order callback failed");
            }
            Object bodyObj = response.getBody();
            Map<?, ?> body = bodyObj instanceof Map<?, ?> map ? map : Map.of();
            boolean accepted = Boolean.TRUE.equals(body.get("accepted"));
            Object reasonValue = body.get("reason");
            String reason = reasonValue == null ? "" : String.valueOf(reasonValue);
            return new OrderPaidSyncResult(accepted, reason);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("order callback failed", ex);
        }
    }

    private String generatePaymentNo() {
        return "PAY" + LocalDateTime.now().format(PAYMENT_NO_FMT);
    }

    private record OrderPaidSyncResult(boolean accepted, String reason) {
    }
}


