-- V40__add_order_assigned_waiter_columns.sql
-- FARELO-314: the "garçom/atendente responsável" (waiter/attendant
-- assigned to a pedido) the Sischef gap analysis calls out
-- (docs/gaps-sischef-pdv.md item 4) — "Garçom Principal", removable and
-- changeable.
--
-- Same denormalized id+name pair convention as
-- order_item.cancelled_by_user_id/_name (V35) — no FK to app_user, so an
-- assignment survives a later rename/deactivation of that user; both
-- nullable, since "no waiter assigned (yet, or anymore)" is the default
-- and a valid permanent state, not a placeholder waiting to be filled.
--
-- Unlike order_item's cancellation columns (a one-way historical fact,
-- written once by OrderItem#cancel and never cleared), this pair is
-- genuinely mutable — OrderService#assignWaiter can change or clear it any
-- number of times over an order's life ("removível/trocável" per the gap
-- analysis), so there is no updatable=false here.

ALTER TABLE orders
    ADD COLUMN assigned_user_id UUID NULL,
    ADD COLUMN assigned_user_name VARCHAR(255) NULL;
