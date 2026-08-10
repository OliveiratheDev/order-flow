package com.start.overflow.notification.adapter.in.messaging;

import com.start.overflow.notification.application.InvalidNotificationEventException;
import com.start.overflow.notification.application.PaymentNotificationCommand;
import com.start.overflow.notification.application.PaymentNotificationService;
import com.start.overflow.shared.messaging.EventEnvelope;
import com.start.overflow.shared.observability.CorrelationIdContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OrderPaidNotificationListenerTest {
    private final PaymentNotificationService service = mock(PaymentNotificationService.class);
    private final OrderPaidNotificationListener listener =
            new OrderPaidNotificationListener(service);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void translatesEnvelopeDelegatesAndPropagatesCorrelationId() {
        EventEnvelope<OrderPaidMessage> envelope = envelope(1, new BigDecimal("249.90"));
        doAnswer(invocation -> {
            assertThat(MDC.get(CorrelationIdContext.MDC_KEY)).isEqualTo("correlation-123");
            return null;
        }).when(service).notifyPayment(any());

        listener.handle(envelope, "correlation-123");

        verify(service).notifyPayment(new PaymentNotificationCommand(
                envelope.eventId(), "correlation-123", 42L, 7L,
                new BigDecimal("249.90")));
        assertThat(MDC.get(CorrelationIdContext.MDC_KEY)).isNull();
    }

    @Test
    void rejectsUnsupportedVersionWithoutRetryAndPreservesPreviousMdc() {
        MDC.put(CorrelationIdContext.MDC_KEY, "previous-correlation");
        EventEnvelope<OrderPaidMessage> envelope = envelope(2, BigDecimal.TEN);

        assertThatThrownBy(() -> listener.handle(envelope, "correlation-123"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasCauseInstanceOf(InvalidNotificationEventException.class);

        verify(service, never()).notifyPayment(any());
        assertThat(MDC.get(CorrelationIdContext.MDC_KEY)).isEqualTo("previous-correlation");
    }

    @Test
    void deterministicApplicationFailureIsRejectedWithoutRetry() {
        doThrow(new InvalidNotificationEventException("amount inválido"))
                .when(service).notifyPayment(any());

        assertThatThrownBy(() -> listener.handle(
                envelope(1, BigDecimal.TEN), "correlation-123"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasCauseInstanceOf(InvalidNotificationEventException.class);
    }

    @Test
    void transientFailureEscapesForContainerRetry() {
        IllegalStateException transientFailure = new IllegalStateException("banco indisponível");
        doThrow(transientFailure).when(service).notifyPayment(any());

        assertThatThrownBy(() -> listener.handle(
                envelope(1, BigDecimal.TEN), "correlation-123"))
                .isSameAs(transientFailure);
    }

    @Test
    void rejectsDivergentCorrelationHeaderWithoutRetry() {
        assertThatThrownBy(() -> listener.handle(
                envelope(1, BigDecimal.TEN), "another-correlation"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasCauseInstanceOf(InvalidNotificationEventException.class);

        verify(service, never()).notifyPayment(any());
    }

    private EventEnvelope<OrderPaidMessage> envelope(int version, BigDecimal amount) {
        return new EventEnvelope<>(
                UUID.randomUUID(), "OrderPaid", version,
                Instant.parse("2026-08-09T20:00:00Z"), "correlation-123",
                new OrderPaidMessage(42L, 7L, amount));
    }
}
