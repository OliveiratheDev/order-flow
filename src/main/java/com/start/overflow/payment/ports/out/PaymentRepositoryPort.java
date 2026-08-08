package com.start.overflow.payment.ports.out;

import com.start.overflow.payment.domain.Payment;

import java.util.Optional;

public interface PaymentRepositoryPort {
    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByIdForUpdate(Long id);

    boolean existsByOrderId(Long orderId);
}
