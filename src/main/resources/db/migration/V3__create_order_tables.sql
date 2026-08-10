CREATE TABLE customer_order (
    id               BIGSERIAL     NOT NULL,
    customer_id      BIGINT        NOT NULL,
    status           VARCHAR(30)   NOT NULL,
    subtotal         NUMERIC(12,2) NOT NULL,
    discount         NUMERIC(12,2) NOT NULL DEFAULT 0,
    total            NUMERIC(12,2) NOT NULL,
    shipping_address VARCHAR(255)  NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version          BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_customer_order PRIMARY KEY (id),
    CONSTRAINT fk_customer_order_customer FOREIGN KEY (customer_id) REFERENCES app_user (id),
    CONSTRAINT ck_customer_order_total CHECK (total >= 0),
    CONSTRAINT ck_customer_order_status CHECK (
        status IN ('CREATED', 'AWAITING_PAYMENT', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED')
    ),
    CONSTRAINT ck_customer_order_subtotal CHECK (subtotal >= 0),
    CONSTRAINT ck_customer_order_discount CHECK (discount >= 0)
);

CREATE INDEX ix_customer_order_customer_created
    ON customer_order (customer_id, created_at DESC);
CREATE INDEX ix_customer_order_status ON customer_order (status);

CREATE TABLE order_item (
    id           BIGSERIAL     NOT NULL,
    order_id     BIGINT        NOT NULL,
    product_id   BIGINT        NOT NULL,
    product_name VARCHAR(120)  NOT NULL,
    sku          VARCHAR(40)   NOT NULL,
    quantity     INTEGER       NOT NULL,
    unit_price   NUMERIC(12,2) NOT NULL,
    line_total   NUMERIC(14,2) NOT NULL,

    CONSTRAINT pk_order_item PRIMARY KEY (id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES customer_order (id),
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT uk_order_item_order_product UNIQUE (order_id, product_id),
    CONSTRAINT ck_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_item_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_order_item_line_total CHECK (line_total >= 0)
);

CREATE INDEX ix_order_item_order_id ON order_item (order_id);
CREATE INDEX ix_order_item_product_id ON order_item (product_id);

COMMENT ON TABLE customer_order IS 'Pedidos com preço capturado no momento da compra';
