-- V49__add_order_print_on_confirm_column.sql
-- FARELO-319: "Imprimir ao confirmar" (docs/gaps-sischef-pdv.md item 9) —
-- a per-order checkbox controlling whether the automatic kitchen-ticket
-- printing that already happens on OrderCreated (FARELO-072) fires for
-- this order. Defaults to true (NOT NULL) so every existing row keeps
-- today's always-print behavior with no data migration.

ALTER TABLE orders
    ADD COLUMN print_on_confirm BOOLEAN NOT NULL DEFAULT true;
