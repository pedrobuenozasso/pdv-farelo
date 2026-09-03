-- V21__create_inventory_movement_table.sql
-- Inventory domain (FARELO-093): the stock ledger. Every stock change is a
-- new, append-only row here — an ingredient's balance is SUM(quantity) over
-- its rows, never a mutable column anywhere (prompt mestre seção 13,
-- AGENTS.md). Follows ingredient (V16), recipe (V17), recipe_item (V19).
--
-- No UPDATE/DELETE is ever issued against this table by application code
-- (see InventoryMovement's javadoc) — every column is NOT NULL (except
-- order_id) with no soft-delete/active flag, because there is no "later
-- state" for an already-written row to be in.
--
-- No updated_at column, unlike every other table in this codebase (compare
-- ingredient/recipe/recipe_item, all of which have both created_at and
-- updated_at) — see InventoryMovement's javadoc for why an append-only
-- ledger row has no "modified later" concept to track.
--
-- ingredient_id: NOT NULL, mirrors InventoryMovement's required @ManyToOne.
--
-- quantity: NUMERIC(12,3), signed (no CHECK forcing a sign) — same
-- precision/scale as recipe_item.quantity (V19), see InventoryMovement's
-- javadoc for why sign (not a separate direction column) expresses
-- in/out.
--
-- type: same VARCHAR + CHECK convention as ingredient.unit (V16) /
-- product.production_station (V14) / command.status (V5) / orders.status
-- (V7), mirroring InventoryMovementType's full seven-value enum — see that
-- enum's javadoc for why the complete prompt-mestre list is used verbatim
-- instead of trimmed to only what FARELO-093 itself produces (it produces
-- none of them; no INSERT happens from application code until FARELO-094+).
--
-- order_id: nullable, plain UUID with a DB-level FK to orders(id) for
-- referential integrity when set (no Java-side @ManyToOne — see
-- InventoryMovement's javadoc for why). Only order-sourced movement types
-- are expected to ever set it.
--
-- *** No uniqueness constraint on order_id/ingredient_id/type here. ***
-- Prompt mestre seção 16 gives the idempotency key example as
-- "ORDER_CONSUMPTION orderId=123 ingredientId=5" and demands "toda operação
-- crítica deve possuir idempotência" — but there is no producer of
-- ORDER_CONSUMPTION anywhere in this codebase yet (FARELO-096 is a future
-- ticket), so there is nothing real yet to test a uniqueness constraint
-- against, and the exact shape of that constraint (which columns, whether
-- it's a plain UNIQUE or a partial index like recipe's V17, how retries are
-- expected to detect "already processed" vs. just get rejected) is
-- explicitly FARELO-097's job ("Implementar idempotência da baixa"), not
-- this one's. This migration only lays the column groundwork so FARELO-097
-- doesn't need its own migration just to add order_id first.

CREATE TABLE inventory_movement (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ingredient_id UUID NOT NULL REFERENCES ingredient (id),
    quantity      NUMERIC(12, 3) NOT NULL,
    type          VARCHAR(30) NOT NULL
        CHECK (type IN (
            'PURCHASE', 'ORDER_CONSUMPTION', 'LOSS', 'ADJUSTMENT',
            'RETURN', 'CANCELLATION', 'INTERNAL_CONSUMPTION'
        )),
    order_id      UUID REFERENCES orders (id),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Supports GET /api/v1/ingredients/{ingredientId}/movements
-- (InventoryMovementRepository#findByIngredientIdOrderByCreatedAtAsc) and
-- the balance sum query (#sumQuantityByIngredientId) — both filter by
-- ingredient_id.
CREATE INDEX idx_inventory_movement_ingredient_id ON inventory_movement (ingredient_id);

-- Not queried by anything in this ticket (no order-sourced producer exists
-- yet), but cheap to add now alongside the column itself, and exactly the
-- shape a future lookup ("all movements for order X") or FARELO-097's
-- idempotency check would want. Deliberately plain, not UNIQUE — see the
-- note above on why enforcing uniqueness is out of scope here.
CREATE INDEX idx_inventory_movement_order_id ON inventory_movement (order_id);
