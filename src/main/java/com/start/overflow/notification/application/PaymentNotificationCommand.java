package com.start.overflow.notification.application;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentNotificationCommand(
        UUID eventId,
        String correlationId,
        Long orderId,
        Long customerId,
        BigDecimal amount
) {
}
