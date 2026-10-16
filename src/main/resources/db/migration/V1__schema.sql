CREATE TABLE IF NOT EXISTS product (
     id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
     sku              VARCHAR(64)  NOT NULL,
     name             VARCHAR(200) NOT NULL,
     description      TEXT,
     unit             VARCHAR(32)  NOT NULL,
     reorder_level    INTEGER      NOT NULL DEFAULT 0,
     quantity_on_hand INTEGER      NOT NULL DEFAULT 0,
     active           BOOLEAN      NOT NULL DEFAULT TRUE,
     created_at       TIMESTAMPTZ  NOT NULL,
     version          BIGINT       NOT NULL DEFAULT 0,
     CONSTRAINT uq_product_sku UNIQUE (sku),
     CONSTRAINT ck_product_quantity_non_negative CHECK (quantity_on_hand >= 0),
     CONSTRAINT ck_product_reorder_level_non_negative CHECK (reorder_level >= 0)
);

CREATE TABLE IF NOT EXISTS stock_movement (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id     BIGINT       NOT NULL REFERENCES product (id),
    type           VARCHAR(16)  NOT NULL,
    quantity_delta INTEGER      NOT NULL,
    balance_after  INTEGER      NOT NULL,
    note           VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_movement_type CHECK (type IN ('RECEIPT', 'ISSUE', 'ADJUSTMENT')),
    CONSTRAINT ck_movement_delta_non_zero CHECK (quantity_delta <> 0),
    CONSTRAINT ck_movement_balance_non_negative CHECK (balance_after >= 0)
    );

CREATE INDEX IF NOT EXISTS idx_stock_movement_product_created
    ON stock_movement (product_id, created_at DESC);