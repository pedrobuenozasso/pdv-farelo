ALTER TABLE product ADD COLUMN gtin VARCHAR(14);

ALTER TABLE product ADD CONSTRAINT product_gtin_shape_check
    CHECK (gtin IS NULL OR gtin = 'SEM GTIN' OR gtin ~ '^[0-9]{8}$|^[0-9]{12,14}$');
