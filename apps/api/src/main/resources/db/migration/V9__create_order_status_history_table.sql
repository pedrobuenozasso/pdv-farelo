-- V9__create_order_status_history_table.sql
-- Ordering domain (FARELO-056): append-only history of Order status
-- transitions (AGENTS.md / prompt mestre seção 9 — never rely only on the
-- current status column).
--
-- from_status is nullable — the very first entry (order creation) has no
-- prior status. to_status is always required. Both use the same VARCHAR +
-- CHECK constraint convention as orders.status (V7__create_order_table.sql).

CREATE TABLE order_status_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID NOT NULL REFERENCES orders (id),
    from_status VARCHAR(30)
        CHECK (from_status IN ('CREATED', 'CONFIRMED', 'PREPARING', 'READY', 'DELIVERED', 'CANCELLED')),
    to_status   VARCHAR(30) NOT NULL
        CHECK (to_status IN ('CREATED', 'CONFIRMED', 'PREPARING', 'READY', 'DELIVERED', 'CANCELLED')),
    changed_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_order_status_history_order_id ON order_status_history (order_id);
