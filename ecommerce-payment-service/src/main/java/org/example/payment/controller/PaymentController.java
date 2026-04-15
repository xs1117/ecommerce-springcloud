package org.example.payment.controller;

import jakarta.validation.Valid;
import org.example.payment.dto.CreatePaymentRequest;
import org.example.payment.dto.MockSuccessRequest;
import org.example.payment.dto.RefundRequest;
import org.example.payment.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create")
    public ResponseEntity<?> create(@Valid @RequestBody CreatePaymentRequest request) {
        return ResponseEntity.ok(paymentService.createPayment(request));
    }

    @PostMapping("/mock-success")
    public ResponseEntity<?> mockSuccess(@Valid @RequestBody MockSuccessRequest request) {
        return ResponseEntity.ok(paymentService.mockSuccess(request));
    }

    @PostMapping("/refund")
    public ResponseEntity<?> refund(@Valid @RequestBody RefundRequest request) {
        return ResponseEntity.ok(paymentService.refund(request));
    }

    @PostMapping("/admin/refund/by-order")
    public ResponseEntity<?> adminRefundByOrder(@RequestParam String orderNo) {
        return ResponseEntity.ok(paymentService.refundByOrderNo(orderNo));
    }

    @GetMapping("/query")
    public ResponseEntity<?> query(@RequestParam String paymentNo) {
        return ResponseEntity.ok(paymentService.query(paymentNo));
    }
}
