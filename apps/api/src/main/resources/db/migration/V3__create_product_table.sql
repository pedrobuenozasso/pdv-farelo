-- V3__create_product_table.sql
-- Catalog domain (FARELO-011): Product, a sellable menu item.
-- No recipe, inventory or advanced fiscal fields yet (see docs/domain-model.md).

CREATE TABLE product (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(120) NOT NULL,
    description TEXT,
    price       NUMERIC(10,2) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    category_id UUID NOT NULL REFERENCES category (id),
    image_url   VARCHAR(2048),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_product_category_id ON product (category_id);
