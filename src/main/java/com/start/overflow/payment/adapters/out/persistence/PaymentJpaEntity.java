package com.start.overflow.payment.adapters.out.persistence;

import com.start.overflow.payment.domain.Payment;
import com.start.overflow.payment.domain.PaymentMethod;
import com.start.overflow.payment.domain.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Table(name = "payment")
public class PaymentJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "external_id", unique = true, length = 100)
    private String externalId;

    @Column(name = "payment_url", length = 500)
    private String paymentUrl;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected PaymentJpaEntity() {
    }

    PaymentJpaEntity(Payment payment) {
        this.orderId = payment.getOrderId();
        this.customerId = payment.getCustomerId();
        this.externalId = payment.getExternalId();
        this.paymentUrl = payment.getPaymentUrl();
        this.amount = payment.getAmount();
        this.method = payment.getMethod();
        this.status = payment.getStatus();
        this.createdAt = payment.getCreatedAt();
        this.updatedAt = payment.getUpdatedAt();
    }

    void apply(Payment payment) {
        this.externalId = payment.getExternalId();
        this.paymentUrl = payment.getPaymentUrl();
        this.status = payment.getStatus();
        this.updatedAt = payment.getUpdatedAt();
    }

    Payment toDomain() {
        return Payment.restore(id, orderId, customerId, amount, method, externalId, paymentUrl,
                status, createdAt, updatedAt);
    }
}
