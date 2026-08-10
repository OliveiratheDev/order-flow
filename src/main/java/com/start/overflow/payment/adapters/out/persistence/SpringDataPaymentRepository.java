package com.start.overflow.payment.adapters.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface SpringDataPaymentRepository extends JpaRepository<PaymentJpaEntity, Long> {
    boolean existsByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentJpaEntity p WHERE p.id = :id")
    Optional<PaymentJpaEntity> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentJpaEntity p WHERE p.externalId = :externalId")
    Optional<PaymentJpaEntity> findByExternalIdForUpdate(
            @Param("externalId") String externalId);

    @Query(value = """
            SELECT p.id
              FROM payment p
              JOIN customer_order o ON o.id = p.order_id
             WHERE p.status = 'PENDING'
               AND o.status = 'AWAITING_PAYMENT'
               AND p.created_at >= :newerThan
               AND p.created_at <= :olderThan
             ORDER BY p.created_at, p.id
            """, nativeQuery = true)
    List<Long> findPendingIdsForReconciliation(
            @Param("newerThan") Instant newerThan,
            @Param("olderThan") Instant olderThan,
            Pageable pageable);
}
