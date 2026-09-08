-- V39__add_order_type_and_nullable_command.sql
-- FARELO-312: adds the BALCAO order type (a walk-up counter sale with no
-- comanda, docs/gaps-sischef-pdv.md item 2) alongside the existing COMANDA
-- flow every order has used until now.
--
-- orders.command_id was NOT NULL since V7 (every order belonged to a
-- comanda) — a BALCAO order has no comanda to attach to, so that
-- constraint is relaxed here. ck_orders_type_command_scope enforces the
-- exclusivity at the database level too, not just by convention: same
-- defense-in-depth pattern as ck_print_job_type_scope (V36) and
-- ck_discount_percentage_scope (V37).
--
-- type defaults to 'COMANDA' so every existing row (all of them, by
-- definition, had a command_id) backfills correctly with no data migration
-- needed beyond the column default.

ALTER TABLE orders
    ALTER COLUMN command_id DROP NOT NULL;

ALTER TABLE orders
    ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'COMANDA'
        CHECK (type IN ('COMANDA', 'BALCAO'));

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_type_command_scope CHECK (
        (type = 'COMANDA' AND command_id IS NOT NULL)
        OR (type = 'BALCAO' AND command_id IS NULL)
    );
