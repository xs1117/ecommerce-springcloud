package org.example.order.dto;

public record OrderQueryRequest(Long userId, String status, Integer limit) {
}

