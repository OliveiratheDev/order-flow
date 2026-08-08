CREATE TABLE payment (
    id          BIGSERIAL     NOT NULL,
    order_id    BIGINT        NOT NULL,
    customer_id BIGINT        NOT NULL,
    external_id VARCHAR(100),
    amount      NUMERIC(12,2) NOT NULL,
    method      VARCHAR(30)   NOT NULL,
    status      VARCHAR(30)   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version     BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_payment PRIMARY KEY (id),
    CONSTRAINT uk_payment_order UNIQUE (order_id),
    CONSTRAINT uk_payment_external_id UNIQUE (external_id),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES customer_order (id),
    CONSTRAINT fk_payment_customer FOREIGN KEY (customer_id) REFERENCES app_user (id),
    CONSTRAINT ck_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_method CHECK (method IN ('CREDIT_CARD', 'PIX', 'BOLETO')),
    CONSTRAINT ck_payment_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

CREATE INDEX ix_payment_customer_created ON payment (customer_id, created_at DESC);
CREATE INDEX ix_payment_status ON payment (status);

COMMENT ON TABLE payment IS 'Cobranças simuladas associadas a pedidos';
