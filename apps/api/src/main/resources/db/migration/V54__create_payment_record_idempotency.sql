-- The claim and its result commit with the append-only payment and audit log.
-- A concurrent retry waits on this primary key and then reads the winner.
CREATE TABLE payment_record_idempotency (
    idempotency_key UUID PRIMARY KEY,
    request_fingerprint CHAR(64) NOT NULL,
    payment_id UUID REFERENCES payment (id),
    response_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT payment_record_idempotency_result_pair
        CHECK ((payment_id IS NULL AND response_json IS NULL)
            OR (payment_id IS NOT NULL AND response_json IS NOT NULL))
);
