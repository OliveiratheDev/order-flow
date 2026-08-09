package com.start.overflow.shared.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        T payload
) {
    public EventEnvelope {
        Objects.requireNonNull(eventId, "O identificador do evento é obrigatório");
        Objects.requireNonNull(occurredAt, "O instante do evento é obrigatório");
        Objects.requireNonNull(payload, "O payload do evento é obrigatório");
        eventType = requireText(eventType, "O tipo do evento é obrigatório");
        correlationId = requireText(correlationId, "O identificador de correlação é obrigatório");
        if (eventVersion < 1) {
            throw new IllegalArgumentException("A versão do evento deve ser positiva");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.strip();
    }
}
