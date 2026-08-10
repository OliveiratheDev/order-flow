package com.start.overflow.order.event;

import java.math.BigDecimal;

public record OrderItemSnapshot(
        Long productId,
        String productName,
        String sku,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
}
