package org.example.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
        @NotNull Long userId,
        @NotEmpty List<@Valid CreateOrderItem> items,
        String remark,
        Long couponId
) {
    public record CreateOrderItem(
            @NotNull Long productId,
            @NotBlank String productName,
            Long storeId,
            String storeName,
            String productImageUrl,
            String productDescription,
            @NotNull BigDecimal unitPrice,
            @NotNull @Min(1) Integer quantity
    ) {
    }
}

