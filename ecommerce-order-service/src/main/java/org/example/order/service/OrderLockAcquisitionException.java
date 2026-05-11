package org.example.order.service;

public class OrderLockAcquisitionException extends RuntimeException {
    public OrderLockAcquisitionException(String message) {
        super(message);
    }
}

