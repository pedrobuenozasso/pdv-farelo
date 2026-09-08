-- V39__create_customer_table.sql
-- FARELO-313: starts the `customer` domain (reserved but unimplemented
-- since FARELO-045/FARELO-050 — see docs/domain-model.md's domain table).
-- A standalone, searchable customer registry — deliberately separate from
-- orders.customer_name/customer_phone (V12, an immutable per-order
-- snapshot) and command.customer_name/customer_phone (V34, the current
-- editable "who is at this comanda right now" text record). Neither of
-- those tables gains a reference to this one in this ticket — see
-- Customer's javadoc for the full reasoning.
--
-- name: required, same VARCHAR(120) ceiling as command.customer_name/
-- category.name-style free text fields elsewhere in this schema.
--
-- phone: optional (a customer can be registered by name alone), stored
-- normalized (digits only, country code prepended) same as
-- command.customer_phone (V34) — same VARCHAR(20) ceiling for the same
-- reason (see that migration's comment). No UNIQUE constraint here on
-- purpose: de-duplication policy is a real product decision out of this
-- ticket's "criar cadastro" scope (see Customer's javadoc).
--
-- Index on phone: the primary "busca" (search) use case is finding a
-- customer by the phone number staff just typed/read off a receipt.

CREATE TABLE customer (
    id          UUID PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    phone       VARCHAR(20),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_customer_phone ON customer (phone);
