-- V41__add_order_note_column.sql
-- FARELO-316: "Observação geral do pedido" — a free-text note at the
-- pedido level (docs/gaps-sischef-pdv.md item 6), distinct from OrderItem's
-- existing cancellation description (V35), which is about one specific
-- line, not the order as a whole.
--
-- Nullable, no length CHECK here — enforced at the DTO layer
-- (CreateOrderRequest/CreateBalcaoOrderRequest's @Size), same convention
-- DiscountRequest.reason already established for a free-text field with an
-- application-level cap but no matching database constraint.

ALTER TABLE orders
    ADD COLUMN note TEXT NULL;
