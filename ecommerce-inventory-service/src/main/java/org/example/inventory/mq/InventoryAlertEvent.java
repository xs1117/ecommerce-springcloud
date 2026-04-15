package org.example.inventory.mq;

public record InventoryAlertEvent(
        String eventId,
        Long productId,
        Integer availableStock,
        Integer warnThreshold
) {
}

