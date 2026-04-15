package org.example.inventory.mq;

public record SimpleOrderEvent(String eventId, String orderNo) {
}

