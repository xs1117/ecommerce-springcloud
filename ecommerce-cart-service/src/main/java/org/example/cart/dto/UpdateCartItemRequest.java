package org.example.cart.dto;

import jakarta.validation.constraints.Min;

public record UpdateCartItemRequest(
        @Min(1) Integer quantity,
        Boolean selected,
        String source,
        String behaviorDetail
) {
}

