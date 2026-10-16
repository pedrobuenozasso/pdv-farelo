-- A unique key serializes concurrent retries of the same creation intent.
-- Rows are inserted and completed in the same transaction as the order,
-- inventory movements and outbox event; a failed creation leaves no claim.
CREATE TABLE order_creation_idempotency (
    idempotency_key UUID PRIMARY KEY,
    request_fingerprint CHAR(64) NOT NULL,
    order_id UUID REFERENCES orders(id),
    response_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT order_creation_idempotency_result_pair
        CHECK ((order_id IS NULL AND response_json IS NULL)
            OR (order_id IS NOT NULL AND response_json IS NOT NULL))
);
