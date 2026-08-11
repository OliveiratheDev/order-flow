package com.start.overflow.notification.application;

import com.start.overflow.notification.adapter.out.persistence.NotificationDeliveryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest(properties = {
        "orderflow.security.jwt.secret=test-secret-with-at-least-thirty-two-bytes",
        "orderflow.bootstrap.admin.enabled=false",
        "spring.cache.type=none",
        "orderflow.payment.reconciliation.cron=0 0 0 1 1 *"
})
@Testcontainers(disabledWithoutDocker = true)
class PaymentNotificationServiceIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired JdbcTemplate jdbc;
    @Autowired PaymentNotificationService notificationService;
    @Autowired ProcessedEventCleanupService cleanupService;
    @Autowired RabbitProperties rabbitProperties;
    @MockitoSpyBean NotificationDeliveryStore deliveries;

    @BeforeEach
    void resetState() {
        reset(deliveries);
        jdbc.execute("TRUNCATE TABLE notification_delivery, processed_event");
    }

    @Test
    void duplicateEventCreatesOneNotification() {
        PaymentNotificationCommand command = command(UUID.randomUUID());

        var first = notificationService.notifyPayment(command);
        var duplicate = notificationService.notifyPayment(command);

        assertThat(first).isEqualTo(NotificationProcessingResult.SENT);
        assertThat(duplicate).isEqualTo(NotificationProcessingResult.DUPLICATE);
        assertThat(count("processed_event")).isEqualTo(1);
        assertThat(count("notification_delivery")).isEqualTo(1);
    }

    @Test
    void concurrentDeliveriesAreDeduplicatedByDatabaseConstraint() throws Exception {
        PaymentNotificationCommand command = command(UUID.randomUUID());
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                start.await();
                return notificationService.notifyPayment(command);
            });
            var second = executor.submit(() -> {
                start.await();
                return notificationService.notifyPayment(command);
            });
            start.countDown();

            List<NotificationProcessingResult> results = List.of(
                    first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertThat(results).containsExactlyInAnyOrder(
                    NotificationProcessingResult.SENT,
                    NotificationProcessingResult.DUPLICATE);
            assertThat(count("processed_event")).isEqualTo(1);
            assertThat(count("notification_delivery")).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void paymentEventDoesNotDependOnCreatedEventOrCurrentOrderState() {
        PaymentNotificationCommand command = command(UUID.randomUUID());

        var result = notificationService.notifyPayment(command);

        assertThat(result).isEqualTo(NotificationProcessingResult.SENT);
        assertThat(count("notification_delivery")).isEqualTo(1);
    }

    @Test
    void notificationFailureRollsBackProcessedMarker() {
        doThrow(new TransientDataAccessResourceException("banco indisponível"))
                .when(deliveries).record(any(), anyString());

        assertThatThrownBy(() -> notificationService.notifyPayment(command(UUID.randomUUID())))
                .isInstanceOf(TransientDataAccessResourceException.class);

        assertThat(count("processed_event")).isZero();
        assertThat(count("notification_delivery")).isZero();
    }

    @Test
    void cleanupRemovesExpiredMarkersAndSimulatedDeliveries() {
        notificationService.notifyPayment(command(UUID.randomUUID()));
        jdbc.update("UPDATE processed_event SET processed_at = ?",
                Instant.parse("2026-07-01T00:00:00Z").atOffset(ZoneOffset.UTC));
        jdbc.update("UPDATE notification_delivery SET delivered_at = ?",
                Instant.parse("2026-07-01T00:00:00Z").atOffset(ZoneOffset.UTC));

        var result = cleanupService.deleteBefore(Instant.parse("2026-08-01T00:00:00Z"));

        assertThat(result.processedEvents()).isEqualTo(1);
        assertThat(result.deliveries()).isEqualTo(1);
        assertThat(count("processed_event")).isZero();
        assertThat(count("notification_delivery")).isZero();
    }

    @Test
    void configuresThreeTotalAttemptsAndBoundedConsumerConcurrency() {
        var listener = rabbitProperties.getListener().getSimple();

        assertThat(listener.getRetry().isEnabled()).isTrue();
        assertThat(listener.getRetry().getMaxAttempts()).isEqualTo(3);
        assertThat(listener.getRetry().getInitialInterval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(listener.getRetry().getMultiplier()).isEqualTo(2);
        assertThat(listener.getRetry().getMaxInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(listener.getDefaultRequeueRejected()).isFalse();
        assertThat(listener.getConcurrency()).isEqualTo(2);
        assertThat(listener.getMaxConcurrency()).isEqualTo(5);
        assertThat(listener.getPrefetch()).isEqualTo(10);
    }

    private PaymentNotificationCommand command(UUID eventId) {
        return new PaymentNotificationCommand(
                eventId, "correlation-123", 999L, 888L, new BigDecimal("249.90"));
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
