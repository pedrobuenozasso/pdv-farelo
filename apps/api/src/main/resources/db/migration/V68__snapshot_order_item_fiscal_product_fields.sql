-- Historical items stay null: current catalog values cannot reconstruct what
-- the customer bought at the time. A future fiscal draft must reject them.
ALTER TABLE order_item
    ADD COLUMN product_name_snapshot VARCHAR(255),
    ADD COLUMN product_gtin_snapshot VARCHAR(14),
    ADD COLUMN fiscal_unit_snapshot VARCHAR(6);
