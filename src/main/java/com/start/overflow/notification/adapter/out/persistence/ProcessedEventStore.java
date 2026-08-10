package com.start.overflow.notification.adapter.out.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

@Repository
public class ProcessedEventStore {
    private final JdbcTemplate jdbc;

    public ProcessedEventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean tryRegister(UUID eventId, String consumer) {
        int inserted = jdbc.update("""
                INSERT INTO processed_event (event_id, consumer)
                VALUES (?, ?)
                ON CONFLICT (event_id, consumer) DO NOTHING
                """, eventId, consumer);
        return inserted == 1;
    }

    public int deleteBefore(Instant cutoff) {
        return jdbc.update(
                "DELETE FROM processed_event WHERE processed_at < ?",
                cutoff.atOffset(ZoneOffset.UTC));
    }
}
