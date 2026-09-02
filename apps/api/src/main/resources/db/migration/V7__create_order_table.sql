-- V7__create_order_table.sql
-- Ordering domain (FARELO-050): Order, first table of Epic 4.
--
-- Table is named "orders" (plural), NOT "order" — ORDER is a reserved SQL
-- keyword (used in ORDER BY), so an unquoted "order" table name would
-- break every raw SQL statement that touches it, including future
-- migrations (e.g. OrderItem's FK in FARELO-051). The migration file keeps
-- the "order_table" name from the ticket for traceability, but the actual
-- table is "orders".
--
-- status uses the same VARCHAR + CHECK constraint convention as
-- command.status (V5__create_command_table.sql), for consistency.
-- No OrderItem yet (FARELO-051), no price snapshot (FARELO-052).

CREATE TABLE orders (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    command_id UUID NOT NULL REFERENCES command (id),
    status     VARCHAR(30) NOT NULL DEFAULT 'CREATED'
        CHECK (status IN ('CREATED', 'CONFIRMED', 'PREPARING', 'READY', 'DELIVERED', 'CANCELLED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_command_id ON orders (command_id);
