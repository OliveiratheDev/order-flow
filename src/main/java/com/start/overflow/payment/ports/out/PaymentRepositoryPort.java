package com.start.overflow.payment.ports.out;

import com.start.overflow.payment.domain.Payment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepositoryPort {
    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByIdForUpdate(Long id);

    Optional<Payment> findByExternalIdForUpdate(String externalId);

    List<Long> findPendingIdsForReconciliation(
            Instant newerThan, Instant olderThan, int limit);

    boolean existsByOrderId(Long orderId);
}
