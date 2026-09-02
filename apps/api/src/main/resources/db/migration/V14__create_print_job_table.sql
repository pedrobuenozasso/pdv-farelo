-- V14__create_print_job_table.sql
-- Printing domain (FARELO-071): a request to print the kitchen/bar ticket
-- for a specific order. See PrintJob's javadoc for the full design
-- rationale (why it references orders and not printer, why content is a
-- frozen snapshot, and the status convention).
--
-- content holds the printed ticket's content (command number, item
-- names/quantities) frozen at creation time — never re-derived from
-- orders/order_item later. Same JSONB + Hibernate native JSON mapping
-- convention as outbox_event.payload (V10__create_outbox_event_table.sql).
--
-- status uses the same VARCHAR + CHECK constraint convention as
-- command.status/orders.status/outbox_event.status.

CREATE TABLE print_job (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id   UUID NOT NULL REFERENCES orders (id),
    content    JSONB NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PRINTED', 'FAILED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_print_job_order_id ON print_job (order_id);

-- A future Edge-Agent-facing consumer (FARELO-072+) will poll for PENDING
-- rows, same shape as outbox_event's status index
-- (V10__create_outbox_event_table.sql) — added now since it's the exact
-- same predictable future access pattern, not a speculative one.
CREATE INDEX idx_print_job_status ON print_job (status);
