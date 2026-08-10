package com.start.overflow.payment.adapters.out.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "orderflow.payment.asaas")
public record AsaasProperties(
        String baseUrl,
        String apiKey,
        String userAgent,
        int paymentDueDays,
        String webhookToken,
        Duration connectTimeout,
        Duration readTimeout
) {
}
