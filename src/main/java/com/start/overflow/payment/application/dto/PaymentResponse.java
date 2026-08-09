package com.start.overflow.payment.application.dto;

import com.start.overflow.payment.domain.PaymentMethod;
import com.start.overflow.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long orderId,
        Long customerId,
        String externalId,
        String paymentUrl,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        String processingMessage,
        Instant createdAt,
        Instant updatedAt
) {
}
