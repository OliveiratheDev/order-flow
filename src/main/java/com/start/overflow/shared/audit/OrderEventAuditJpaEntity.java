package com.start.overflow.shared.audit;

import com.start.overflow.order.event.OrderDomainEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_event_audit")
public class OrderEventAuditJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected OrderEventAuditJpaEntity() {
    }

    OrderEventAuditJpaEntity(OrderDomainEvent event) {
        this.eventId = event.eventId();
        this.eventType = event.eventType();
        this.orderId = event.orderId();
        this.occurredAt = event.occurredAt();
        this.recordedAt = Instant.now();
    }
}
