CREATE TABLE processed_event (
    event_id     UUID        NOT NULL,
    consumer     VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_processed_event PRIMARY KEY (event_id, consumer)
);

CREATE INDEX ix_processed_event_processed_at
    ON processed_event (processed_at);

CREATE TABLE notification_delivery (
    id           BIGSERIAL     NOT NULL,
    event_id     UUID          NOT NULL,
    consumer     VARCHAR(80)   NOT NULL,
    order_id     BIGINT        NOT NULL,
    customer_id  BIGINT        NOT NULL,
    amount       NUMERIC(19,2) NOT NULL,
    channel      VARCHAR(20)   NOT NULL,
    delivered_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_notification_delivery PRIMARY KEY (id),
    CONSTRAINT ck_notification_delivery_channel CHECK (channel IN ('EMAIL')),
    CONSTRAINT ck_notification_delivery_amount CHECK (amount > 0)
);

CREATE INDEX ix_notification_delivery_event
    ON notification_delivery (event_id, consumer);

CREATE INDEX ix_notification_delivery_delivered_at
    ON notification_delivery (delivered_at);
