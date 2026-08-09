package com.start.overflow.payment.adapters.out.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orderflow.payment.asaas")
public record AsaasProperties(
        String baseUrl,
        String apiKey,
        String userAgent,
        int paymentDueDays,
        String webhookToken
) {
}
