ALTER TABLE payment
    DROP CONSTRAINT ck_payment_status;

ALTER TABLE payment
    ADD CONSTRAINT ck_payment_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'REFUNDED'));

CREATE TABLE webhook_event_log (
    id               BIGSERIAL    NOT NULL,
    event_id         VARCHAR(120) NOT NULL,
    event_type       VARCHAR(60)  NOT NULL,
    payload          JSONB        NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    duplicate_count  INTEGER      NOT NULL DEFAULT 0,
    failure_reason   VARCHAR(255),
    received_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_received_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at     TIMESTAMPTZ,

    CONSTRAINT pk_webhook_event_log PRIMARY KEY (id),
    CONSTRAINT uk_webhook_event_log_event UNIQUE (event_id),
    CONSTRAINT ck_webhook_event_log_status
        CHECK (status IN ('RECEIVED', 'PROCESSED', 'DUPLICATE', 'FAILED')),
    CONSTRAINT ck_webhook_event_log_duplicate_count CHECK (duplicate_count >= 0)
);

CREATE INDEX ix_webhook_event_log_status_received
    ON webhook_event_log (status, received_at);

COMMENT ON TABLE webhook_event_log IS
    'Auditoria e deduplicacao dos eventos recebidos por webhook';
COMMENT ON COLUMN webhook_event_log.payload IS
    'Corpo JSON bruto recebido do provedor para auditoria financeira';
