package org.example.inventory.mq;

public record InventoryReservedEvent(
        String eventId,
        String orderNo,
        boolean success,
        String reason
) {
}

