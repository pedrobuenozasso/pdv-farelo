-- V8__create_order_item_table.sql
-- Ordering domain (FARELO-051): OrderItem, a line item within an Order.
--
-- unit_price is a frozen snapshot captured at sale time — never derived
-- from product.price (AGENTS.md price-snapshot convention). The column
-- exists now; the logic that actually captures it automatically is
-- FARELO-052/053.
--
-- No CHECK on quantity being positive — that validation belongs to the
-- DTO/service layer once the endpoint exists (FARELO-053), not this
-- migration.

CREATE TABLE order_item (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id   UUID NOT NULL REFERENCES orders (id),
    product_id UUID NOT NULL REFERENCES product (id),
    quantity   INTEGER NOT NULL,
    unit_price NUMERIC(10,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_order_item_order_id ON order_item (order_id);
CREATE INDEX idx_order_item_product_id ON order_item (product_id);
