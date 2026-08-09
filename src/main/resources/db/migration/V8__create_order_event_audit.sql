CREATE TABLE order_event_audit (
    id          BIGSERIAL    NOT NULL,
    event_id    UUID         NOT NULL,
    event_type  VARCHAR(60)  NOT NULL,
    order_id    BIGINT       NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL,
    recorded_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_order_event_audit PRIMARY KEY (id),
    CONSTRAINT uk_order_event_audit_event UNIQUE (event_id),
    CONSTRAINT fk_order_event_audit_order
        FOREIGN KEY (order_id) REFERENCES customer_order (id)
);

CREATE INDEX ix_order_event_audit_order_recorded
    ON order_event_audit (order_id, recorded_at DESC);

COMMENT ON TABLE order_event_audit IS
    'Trilha transacional dos eventos de domínio emitidos pelo agregado de pedido';
