package com.start.overflow.notification.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("orderflow.notification")
public record NotificationRetentionProperties(Duration processedEventRetention) {
    public NotificationRetentionProperties {
        if (processedEventRetention == null
                || processedEventRetention.isZero()
                || processedEventRetention.isNegative()) {
            throw new IllegalStateException(
                    "NOTIFICATION_PROCESSED_EVENT_RETENTION deve ser maior que zero");
        }
    }
}
