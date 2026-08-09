package com.start.overflow.shared.messaging;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventEnvelopeTest {

    @Test
    void normalizesContractMetadata() {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-08-09T20:00:00Z");

        EventEnvelope<Map<String, Long>> envelope = new EventEnvelope<>(
                eventId, " OrderCreated ", 1, occurredAt, " correlation-123 ",
                Map.of("orderId", 42L));

        assertThat(envelope.eventId()).isEqualTo(eventId);
        assertThat(envelope.eventType()).isEqualTo("OrderCreated");
        assertThat(envelope.eventVersion()).isEqualTo(1);
        assertThat(envelope.occurredAt()).isEqualTo(occurredAt);
        assertThat(envelope.correlationId()).isEqualTo("correlation-123");
        assertThat(envelope.payload()).containsEntry("orderId", 42L);
    }

    @Test
    void rejectsIncompleteOrUnversionedEnvelope() {
        Instant occurredAt = Instant.parse("2026-08-09T20:00:00Z");

        assertThatThrownBy(() -> new EventEnvelope<>(
                UUID.randomUUID(), "OrderCreated", 0, occurredAt,
                "correlation-123", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("versão");
        assertThatThrownBy(() -> new EventEnvelope<>(
                UUID.randomUUID(), " ", 1, occurredAt,
                "correlation-123", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo");
    }
}
