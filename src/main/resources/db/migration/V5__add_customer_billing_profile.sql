ALTER TABLE app_user
    ADD COLUMN document VARCHAR(14);

ALTER TABLE app_user
    ADD CONSTRAINT uk_app_user_document UNIQUE (document),
    ADD CONSTRAINT ck_app_user_document_length
        CHECK (document IS NULL OR LENGTH(document) IN (11, 14));

ALTER TABLE payment
    ADD COLUMN payment_url VARCHAR(500);

COMMENT ON COLUMN app_user.document IS 'CPF ou CNPJ normalizado do cliente';
COMMENT ON COLUMN payment.payment_url IS 'URL externa para conclusão da cobrança';
COMMENT ON TABLE payment IS 'Cobranças de pedidos, simuladas ou processadas por gateway externo';
