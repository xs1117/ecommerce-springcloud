package org.example.cart.dto;

import jakarta.validation.constraints.NotBlank;

public record CartBehaviorRequest(
        @NotBlank String action,
        Long productId,
        String itemId,
        Integer quantity,
        String source,
        String detail
) {
}

