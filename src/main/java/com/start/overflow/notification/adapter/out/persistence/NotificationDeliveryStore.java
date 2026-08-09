package com.start.overflow.notification.adapter.out.persistence;

import com.start.overflow.notification.application.PaymentNotificationCommand;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;

@Repository
public class NotificationDeliveryStore {
    private static final String CHANNEL = "EMAIL";

    private final JdbcTemplate jdbc;

    public NotificationDeliveryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(PaymentNotificationCommand command, String consumer) {
        jdbc.update("""
                INSERT INTO notification_delivery
                    (event_id, consumer, order_id, customer_id, amount, channel)
                VALUES (?, ?, ?, ?, ?, ?)
                """, command.eventId(), consumer, command.orderId(), command.customerId(),
                command.amount(), CHANNEL);
    }

    public int deleteBefore(Instant cutoff) {
        return jdbc.update(
                "DELETE FROM notification_delivery WHERE delivered_at < ?",
                cutoff.atOffset(ZoneOffset.UTC));
    }
}
