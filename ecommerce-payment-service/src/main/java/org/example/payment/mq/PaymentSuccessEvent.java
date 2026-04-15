package org.example.payment.mq;

import java.math.BigDecimal;

public record PaymentSuccessEvent(
        String eventId,
        String orderNo,
        String paymentNo,
        BigDecimal paidAmount
) {
}

