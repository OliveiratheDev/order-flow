package com.start.overflow.payment.application.dto;

import com.start.overflow.payment.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record CreatePaymentRequest(
        @NotNull Long orderId,
        @NotNull PaymentMethod method
) {
}
