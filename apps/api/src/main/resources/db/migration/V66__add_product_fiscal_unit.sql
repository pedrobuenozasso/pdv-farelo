ALTER TABLE product
    ADD COLUMN fiscal_unit VARCHAR(6),
    ADD CONSTRAINT ck_product_fiscal_unit
        CHECK (fiscal_unit IS NULL OR fiscal_unit ~ '^[A-Z0-9]{1,6}$');
