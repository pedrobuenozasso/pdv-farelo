-- FARELO-321: persistent catalog configuration only. Checkout pricing is separate.
CREATE TABLE product_step (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES product (id),
    label VARCHAR(120) NOT NULL,
    min_choices INTEGER NOT NULL,
    max_choices INTEGER NOT NULL,
    display_order INTEGER NOT NULL,
    level VARCHAR(80) NOT NULL,
    charge_type VARCHAR(40) NOT NULL,
    CONSTRAINT ck_product_step_label CHECK (length(trim(label)) > 0),
    CONSTRAINT ck_product_step_limits CHECK (min_choices >= 0 AND max_choices >= min_choices),
    CONSTRAINT ck_product_step_order CHECK (display_order >= 0),
    CONSTRAINT uk_product_step_order UNIQUE (product_id, display_order)
);

CREATE INDEX idx_product_step_product_id ON product_step (product_id);

CREATE TABLE product_step_option (
    id UUID PRIMARY KEY,
    step_id UUID NOT NULL REFERENCES product_step (id),
    option_product_id UUID NOT NULL REFERENCES product (id),
    display_order INTEGER NOT NULL,
    price NUMERIC(10,2) NOT NULL,
    CONSTRAINT ck_product_step_option_order CHECK (display_order >= 0),
    CONSTRAINT ck_product_step_option_price CHECK (price >= 0),
    CONSTRAINT uk_product_step_option_product UNIQUE (step_id, option_product_id),
    CONSTRAINT uk_product_step_option_order UNIQUE (step_id, display_order)
);

CREATE INDEX idx_product_step_option_product_id ON product_step_option (option_product_id);
