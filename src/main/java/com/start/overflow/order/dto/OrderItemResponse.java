package com.start.overflow.order.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long productId,
        String productName,
        String sku,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
}
