package org.example.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record RefundRequest(@NotBlank String paymentNo, String reason) {
}

