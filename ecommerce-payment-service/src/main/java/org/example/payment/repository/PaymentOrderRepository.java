package org.example.payment.repository;

import org.example.payment.domain.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
    Optional<PaymentOrder> findByPaymentNo(String paymentNo);
    Optional<PaymentOrder> findByOrderNo(String orderNo);
}

