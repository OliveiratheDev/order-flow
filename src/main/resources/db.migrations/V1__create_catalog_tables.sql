
CREATE TABLE category (
                          id          BIGSERIAL    NOT NULL,
                          name        VARCHAR(80)  NOT NULL,
                          slug        VARCHAR(80)  NOT NULL,
                          description VARCHAR(255),
                          active      BOOLEAN      NOT NULL DEFAULT TRUE,
                          created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                          updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                          version     BIGINT       NOT NULL DEFAULT 0,

                          CONSTRAINT pk_category            PRIMARY KEY (id),
                          CONSTRAINT uk_category_slug       UNIQUE (slug),
                          CONSTRAINT ck_category_name_blank CHECK (LENGTH(TRIM(name)) > 0)
);

CREATE INDEX ix_category_active ON category (active);

CREATE TABLE product (
                         id          BIGSERIAL      NOT NULL,
                         category_id BIGINT         NOT NULL,
                         name        VARCHAR(120)   NOT NULL,
                         sku         VARCHAR(40)    NOT NULL,
                         description TEXT,
                         price       NUMERIC(12,2)  NOT NULL,
                         stock       INTEGER        NOT NULL DEFAULT 0,
                         active      BOOLEAN        NOT NULL DEFAULT TRUE,
                         created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
                         updated_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
                         version     BIGINT         NOT NULL DEFAULT 0,

                         CONSTRAINT pk_product          PRIMARY KEY (id),
                         CONSTRAINT uk_product_sku      UNIQUE (sku),
                         CONSTRAINT fk_product_category FOREIGN KEY (category_id)
                             REFERENCES category (id),
                         CONSTRAINT ck_product_price    CHECK (price >= 0),
                         CONSTRAINT ck_product_stock    CHECK (stock >= 0)
);

CREATE INDEX ix_product_category_id ON product (category_id);
CREATE INDEX ix_product_active      ON product (active);

COMMENT ON TABLE  category      IS 'Categorias do catálogo';
COMMENT ON COLUMN category.slug IS 'Identificador legível e único usado em URL';
COMMENT ON COLUMN product.sku   IS 'Código único do item no catálogo';