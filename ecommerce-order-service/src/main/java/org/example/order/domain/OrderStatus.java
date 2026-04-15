package org.example.order.domain;

public enum OrderStatus {
    CREATED,
    STOCK_CONFIRMED,
    STOCK_FAILED,
    WAIT_PAY,
    PAID,
    TO_SHIP,
    TO_RECEIVE,
    FINISHED,
    AFTER_SALE,
    CLOSED,
    REFUNDED
}

