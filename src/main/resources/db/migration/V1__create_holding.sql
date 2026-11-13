CREATE TABLE holding (
    id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code       VARCHAR(10)   NOT NULL,
    name       VARCHAR(50),
    market     VARCHAR(10),
    lots       INT           NOT NULL,
    buy_price  DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_holding_code UNIQUE (code)
);
