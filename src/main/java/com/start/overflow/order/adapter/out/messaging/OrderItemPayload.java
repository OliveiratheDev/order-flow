package com.start.overflow.order.adapter.out.messaging;

import java.math.BigDecimal;

public record OrderItemPayload(
        Long productId,
        String productName,
        String sku,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
}
