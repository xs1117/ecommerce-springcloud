package org.example.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AdjustStockRequest(
        @NotNull Long productId,
        @NotNull @Min(0) Integer totalStock,
        @NotNull @Min(0) Integer warnThreshold
) {
}

