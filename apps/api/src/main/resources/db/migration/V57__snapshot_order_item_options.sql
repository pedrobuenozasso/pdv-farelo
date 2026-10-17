CREATE TABLE order_item_option (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_item_id UUID NOT NULL REFERENCES order_item (id),
    step_id UUID NOT NULL,
    step_name VARCHAR(120) NOT NULL,
    option_id UUID NOT NULL,
    option_product_id UUID NOT NULL,
    option_name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(10,2) NOT NULL CHECK (unit_price >= 0),
    CONSTRAINT uk_order_item_option UNIQUE (order_item_id, option_id)
);

CREATE INDEX idx_order_item_option_item_id ON order_item_option (order_item_id);
