package com.start.overflow.payment.ports.in;

import com.start.overflow.payment.application.dto.CreatePaymentRequest;
import com.start.overflow.payment.application.dto.PaymentResponse;

public interface CreatePaymentUseCase {
    PaymentResponse create(CreatePaymentRequest request);
}
