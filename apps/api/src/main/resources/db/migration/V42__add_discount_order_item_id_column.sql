-- V42__add_discount_order_item_id_column.sql
-- FARELO-317: "Desconto por item" — extends the existing discount domain
-- (FARELO-230/231/232, V37) to also apply to one specific OrderItem, not
-- only a comanda's whole total. Reuses the `discount` table/entity rather
-- than a new one (see Discount's class-level comment) — order_item_id is
-- nullable: NULL for a comanda-wide discount (every row before this
-- ticket), populated for an item-scoped one. command_id stays NOT NULL and
-- populated either way — an item-scoped row denormalizes it from the
-- item's order's command, which is what keeps the existing
-- sum_discounted_amount_by_command aggregate (and so
-- PaymentService#getBalance's "total discount" subtraction) correct with
-- no query change: it already sums every discount row for a command
-- regardless of scope.

ALTER TABLE discount
    ADD COLUMN order_item_id UUID NULL REFERENCES order_item (id);

CREATE INDEX idx_discount_order_item_id ON discount (order_item_id);
