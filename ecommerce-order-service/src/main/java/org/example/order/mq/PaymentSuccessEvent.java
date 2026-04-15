package org.example.order.mq;

import java.math.BigDecimal;

public record PaymentSuccessEvent(
        String eventId,
        String orderNo,
        String paymentNo,
        BigDecimal paidAmount
) {
}

