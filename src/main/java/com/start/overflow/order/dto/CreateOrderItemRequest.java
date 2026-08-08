package com.start.overflow.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateOrderItemRequest(
        @NotNull Long productId,
        @Positive int quantity
) {
}
