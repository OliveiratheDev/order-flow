CREATE TABLE app_user (
    id            BIGSERIAL    NOT NULL,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(160) NOT NULL,
    password_hash VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uk_app_user_email UNIQUE (email),
    CONSTRAINT ck_app_user_name_blank CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_app_user_role CHECK (role IN ('CUSTOMER', 'ADMIN'))
);

CREATE INDEX ix_app_user_active ON app_user (active);

COMMENT ON TABLE app_user IS 'Usuários e clientes autenticáveis do OrderFlow';
