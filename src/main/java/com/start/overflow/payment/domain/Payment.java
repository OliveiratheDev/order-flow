package com.start.overflow.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;

public final class Payment {
    private final Long id;
    private final Long orderId;
    private final Long customerId;
    private final PaymentAmount amount;
    private final PaymentMethod method;
    private String externalId;
    private String paymentUrl;
    private PaymentStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Payment(Long id, Long orderId, Long customerId, PaymentAmount amount,
                    PaymentMethod method, String externalId, String paymentUrl, PaymentStatus status,
                    Instant createdAt, Instant updatedAt) {
        if (orderId == null || customerId == null || amount == null || method == null
                || status == null || createdAt == null || updatedAt == null) {
            throw new PaymentException("Pedido, cliente, método e status são obrigatórios");
        }
        this.id = id;
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.method = method;
        this.externalId = normalizeExternalId(externalId);
        this.paymentUrl = normalizePaymentUrl(paymentUrl);
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Payment create(Long orderId, Long customerId,
                                 BigDecimal amount, PaymentMethod method) {
        Instant now = Instant.now();
        return new Payment(null, orderId, customerId, new PaymentAmount(amount), method, null, null,
                PaymentStatus.PENDING, now, now);
    }

    public static Payment restore(Long id, Long orderId, Long customerId,
                                  BigDecimal amount, PaymentMethod method, String externalId,
                                  String paymentUrl, PaymentStatus status, Instant createdAt,
                                  Instant updatedAt) {
        return new Payment(id, orderId, customerId, new PaymentAmount(amount), method, externalId,
                paymentUrl,
                status, createdAt, updatedAt);
    }

    public void completeCharge(GatewayChargeResult result) {
        if (result == null) {
            throw new PaymentException("O resultado do gateway é obrigatório");
        }
        ensurePending("Apenas pagamentos pendentes podem receber o resultado do gateway");
        this.externalId = normalizeRequiredExternalId(result.externalId());
        this.paymentUrl = normalizePaymentUrl(result.paymentUrl());
        switch (result.status()) {
            case APPROVED -> approve();
            case REJECTED -> reject();
            case PENDING -> touch();
        }
    }

    public void approve() {
        ensurePending("Apenas pagamentos pendentes podem ser aprovados");
        this.status = PaymentStatus.APPROVED;
        touch();
    }

    public void reject() {
        ensurePending("Apenas pagamentos pendentes podem ser rejeitados");
        this.status = PaymentStatus.REJECTED;
        touch();
    }

    public void cancel() {
        ensurePending("Apenas pagamentos pendentes podem ser cancelados");
        this.status = PaymentStatus.CANCELLED;
        touch();
    }

    public void refund() {
        if (status != PaymentStatus.APPROVED) {
            throw new PaymentException("Apenas pagamentos aprovados podem ser estornados");
        }
        this.status = PaymentStatus.REFUNDED;
        touch();
    }

    public void markDivergent(GatewayChargeResult result) {
        if (result == null) {
            throw new PaymentException("O resultado do gateway é obrigatório");
        }
        ensurePending("Apenas pagamentos pendentes podem ser marcados como divergentes");
        if (this.externalId == null) {
            this.externalId = normalizeRequiredExternalId(result.externalId());
        }
        this.paymentUrl = normalizePaymentUrl(result.paymentUrl());
        this.status = PaymentStatus.DIVERGENT;
        touch();
    }

    private void ensurePending(String message) {
        if (status != PaymentStatus.PENDING) {
            throw new PaymentException(message);
        }
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    private static String normalizeExternalId(String value) {
        return value == null ? null : normalizeRequiredExternalId(value);
    }

    private static String normalizeRequiredExternalId(String value) {
        if (value == null || value.isBlank()) {
            throw new PaymentException("O identificador externo do pagamento é obrigatório");
        }
        String normalized = value.strip();
        if (normalized.length() > 100) {
            throw new PaymentException("O identificador externo deve ter no máximo 100 caracteres");
        }
        return normalized;
    }

    private static String normalizePaymentUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > 500) {
            throw new PaymentException("A URL de pagamento deve ter no máximo 500 caracteres");
        }
        return normalized;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public Long getCustomerId() { return customerId; }
    public BigDecimal getAmount() { return amount.value(); }
    public PaymentMethod getMethod() { return method; }
    public String getExternalId() { return externalId; }
    public String getPaymentUrl() { return paymentUrl; }
    public PaymentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
