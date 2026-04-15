package org.example.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LockRequest(
        @NotBlank String orderNo,
        @NotBlank String reservationNo,
        @NotNull Long productId,
        @NotNull @Min(1) Integer quantity
) {
}

