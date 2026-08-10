package com.start.overflow.notification.adapter.in.messaging;

import java.math.BigDecimal;

public record OrderPaidMessage(
        Long orderId,
        Long customerId,
        BigDecimal total
) {
}
