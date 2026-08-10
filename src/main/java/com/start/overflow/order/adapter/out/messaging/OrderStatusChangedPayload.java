package com.start.overflow.order.adapter.out.messaging;

import java.math.BigDecimal;

public record OrderStatusChangedPayload(
        Long orderId,
        Long customerId,
        BigDecimal total
) {
}
