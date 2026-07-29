
CREATE TABLE categoria (
                           id BIGSERIAL PRIMARY KEY,
                           nome VARCHAR(100) NOT NULL,
                           descricao VARCHAR(255)
);


CREATE TABLE produto (
                         id BIGSERIAL PRIMARY KEY,
                         nome VARCHAR(150) NOT NULL,
                         descricao VARCHAR(255),
                         preco DECIMAL(10,2) NOT NULL,
                         quantidade INT NOT NULL DEFAULT 0,
                         categoria_id BIGINT NOT NULL,

                         CONSTRAINT fk_produto_categoria
                             FOREIGN KEY (categoria_id)
                                 REFERENCES categoria(id)
);