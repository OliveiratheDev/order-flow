package com.start.overflow.payment.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("orderflow.payment.reconciliation")
public record PaymentReconciliationProperties(
        String cron,
        Duration minAge,
        Duration maxAge,
        int batchSize,
        Duration lockAtMostFor,
        Duration lockAtLeastFor
) {
    public PaymentReconciliationProperties {
        if (cron == null || cron.isBlank()) {
            throw new IllegalStateException("PAYMENT_RECONCILIATION_CRON é obrigatório");
        }
        requirePositive(minAge, "PAYMENT_RECONCILIATION_MIN_AGE");
        requirePositive(maxAge, "PAYMENT_RECONCILIATION_MAX_AGE");
        requirePositive(lockAtMostFor, "PAYMENT_RECONCILIATION_LOCK_AT_MOST");
        if (lockAtLeastFor == null || lockAtLeastFor.isNegative()) {
            throw new IllegalStateException(
                    "PAYMENT_RECONCILIATION_LOCK_AT_LEAST não pode ser negativo");
        }
        if (maxAge.compareTo(minAge) <= 0) {
            throw new IllegalStateException(
                    "A idade máxima da conciliação deve superar a idade mínima");
        }
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalStateException(
                    "PAYMENT_RECONCILIATION_BATCH_SIZE deve estar entre 1 e 1000");
        }
        if (lockAtMostFor.compareTo(lockAtLeastFor) <= 0) {
            throw new IllegalStateException(
                    "O tempo máximo do lock deve superar o tempo mínimo");
        }
    }

    private static void requirePositive(Duration value, String property) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(property + " deve ser maior que zero");
        }
    }
}
