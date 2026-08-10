ALTER TABLE payment
    DROP CONSTRAINT ck_payment_status;

ALTER TABLE payment
    ADD CONSTRAINT ck_payment_status
        CHECK (status IN (
            'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'REFUNDED', 'DIVERGENT'
        ));

CREATE INDEX ix_payment_reconciliation
    ON payment (status, created_at)
    WHERE status = 'PENDING';

CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,

    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);

COMMENT ON TABLE shedlock IS
    'Locks distribuídos dos jobs agendados executados pelas instâncias da aplicação';
