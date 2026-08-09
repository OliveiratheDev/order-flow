package com.start.overflow.payment.adapters.in.webhook;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("payment-asaas")
public class WebhookEventLogStore {
    private final JdbcTemplate jdbc;

    public WebhookEventLogStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int tryRegister(String eventId, String eventType, String payload) {
        return jdbc.update("""
                INSERT INTO webhook_event_log
                    (event_id, event_type, payload, status)
                VALUES (?, ?, CAST(? AS jsonb), 'RECEIVED')
                ON CONFLICT (event_id) DO NOTHING
                """, eventId, eventType, payload);
    }

    public WebhookEventStatus lockStatus(String eventId) {
        return jdbc.queryForObject("""
                SELECT status
                FROM webhook_event_log
                WHERE event_id = ?
                FOR UPDATE
                """, WebhookEventStatus.class, eventId);
    }

    public void prepareRetry(String eventId, String eventType, String payload) {
        jdbc.update("""
                UPDATE webhook_event_log
                SET event_type = ?, payload = CAST(? AS jsonb), status = 'RECEIVED',
                    failure_reason = NULL, last_received_at = NOW()
                WHERE event_id = ?
                """, eventType, payload, eventId);
    }

    public void markProcessed(String eventId) {
        jdbc.update("""
                UPDATE webhook_event_log
                SET status = 'PROCESSED', failure_reason = NULL,
                    processed_at = NOW(), last_received_at = NOW()
                WHERE event_id = ?
                """, eventId);
    }

    public void markDuplicate(String eventId) {
        jdbc.update("""
                UPDATE webhook_event_log
                SET status = 'DUPLICATE', duplicate_count = duplicate_count + 1,
                    last_received_at = NOW()
                WHERE event_id = ?
                """, eventId);
    }

    public void recordFailure(String eventId, String eventType, String payload, String reason) {
        jdbc.update("""
                INSERT INTO webhook_event_log
                    (event_id, event_type, payload, status, failure_reason)
                VALUES (?, ?, CAST(? AS jsonb), 'FAILED', ?)
                ON CONFLICT (event_id) DO UPDATE
                SET event_type = EXCLUDED.event_type,
                    payload = EXCLUDED.payload,
                    status = 'FAILED',
                    failure_reason = EXCLUDED.failure_reason,
                    last_received_at = NOW()
                """, eventId, eventType, payload, truncate(reason));
    }

    private String truncate(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Falha sem detalhe disponível";
        }
        String safe = reason.strip();
        return safe.length() <= 255 ? safe : safe.substring(0, 255);
    }
}
