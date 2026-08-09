package com.start.overflow.payment.adapters.out.persistence;

import com.start.overflow.payment.domain.Payment;
import com.start.overflow.payment.ports.out.PaymentRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class PaymentPersistenceAdapter implements PaymentRepositoryPort {
    private final SpringDataPaymentRepository repository;

    public PaymentPersistenceAdapter(SpringDataPaymentRepository repository) {
        this.repository = repository;
    }

    @Override
    public Payment save(Payment payment) {
        PaymentJpaEntity entity;
        if (payment.getId() == null) {
            entity = new PaymentJpaEntity(payment);
        } else {
            entity = repository.findById(payment.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Pagamento não encontrado durante a persistência"));
            entity.apply(payment);
        }
        return repository.save(entity).toDomain();
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return repository.findById(id).map(PaymentJpaEntity::toDomain);
    }

    @Override
    public Optional<Payment> findByIdForUpdate(Long id) {
        return repository.findByIdForUpdate(id).map(PaymentJpaEntity::toDomain);
    }

    @Override
    public Optional<Payment> findByExternalIdForUpdate(String externalId) {
        return repository.findByExternalIdForUpdate(externalId).map(PaymentJpaEntity::toDomain);
    }

    @Override
    public List<Long> findPendingIdsForReconciliation(
            Instant newerThan, Instant olderThan, int limit) {
        return repository.findPendingIdsForReconciliation(
                newerThan, olderThan, PageRequest.of(0, limit));
    }

    @Override
    public boolean existsByOrderId(Long orderId) {
        return repository.existsByOrderId(orderId);
    }
}
