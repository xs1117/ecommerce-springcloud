package org.example.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AddCartItemRequest(
        @NotNull Long productId,
        Long storeId,
        String storeName,
        @NotBlank String title,
        String description,
        String coverImageUrl,
        @NotNull BigDecimal price,
        Integer maxQuantity,
        @Min(1) Integer quantity,
        Boolean selected,
        String source,
        String behaviorDetail
) {
}

