package org.example.order.mq;

import java.math.BigDecimal;
import java.util.List;

public record OrderCreatedEvent(
        String eventId,
        String orderNo,
        Long userId,
        BigDecimal payAmount,
        List<OrderSkuItem> items
) {
    public record OrderSkuItem(Long productId, Integer quantity) {}
}

