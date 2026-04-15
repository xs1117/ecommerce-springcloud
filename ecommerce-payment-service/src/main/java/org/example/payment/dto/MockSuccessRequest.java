package org.example.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record MockSuccessRequest(@NotBlank String paymentNo) {
}

