package com.start.overflow.payment.ports.in;

import com.start.overflow.payment.application.dto.PaymentResponse;

public interface CancelPaymentUseCase {
    PaymentResponse cancel(Long id);
}
