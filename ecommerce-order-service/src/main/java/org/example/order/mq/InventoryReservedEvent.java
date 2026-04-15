package org.example.order.mq;

public record InventoryReservedEvent(
        String eventId,
        String orderNo,
        boolean success,
        String reason
) {
}

