package com.start.overflow.shared.idempotency;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "orderflow.idempotency")
public record IdempotencyProperties(Duration processingTtl, Duration retentionTtl) {
    public IdempotencyProperties {
        if (processingTtl == null || processingTtl.isZero() || processingTtl.isNegative()) {
            throw new IllegalArgumentException("O TTL de processamento deve ser positivo");
        }
        if (retentionTtl == null || retentionTtl.isZero() || retentionTtl.isNegative()) {
            throw new IllegalArgumentException("O TTL de retenção deve ser positivo");
        }
    }
}
