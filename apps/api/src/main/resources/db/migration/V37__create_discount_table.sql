-- V37__create_discount_table.sql
-- Discount domain (FARELO-230/231/232): a fixed-amount or percentage
-- reduction applied to a comanda's total. Same append-only-ledger shape
-- as payment (V26__create_payment_table.sql) — every column updatable=false
-- in the entity, no update/delete path, a wrong entry is corrected by a
-- future offsetting record, not an edit.
--
-- original_amount/discounted_amount are frozen snapshots (FARELO-231:
-- "Backend deve calcular o valor final usando BigDecimal") — a percentage
-- discount's monetary value is computed once, at application time, against
-- the comanda's totalOwed then, and never recomputed later even if items
-- are added/removed afterward. original_amount also satisfies FARELO-232's
-- explicit "valor original" audit requirement.
--
-- percentage is nullable: only populated for type = 'PERCENTAGE' (the rate
-- used, e.g. 10.00 for "10%"); ck_discount_percentage_scope enforces that
-- exclusivity at the database level, same defense-in-depth convention as
-- ck_print_job_type_scope (V36) / ck_order_item_other_reason_requires_description (V35).
--
-- reason is nullable (FARELO-232 leaves it optional — see DiscountRequest's
-- javadoc for why no "obrigatório conforme configuração" toggle exists
-- yet). applied_by_user_id/applied_by_user_name: same denormalized
-- operator pair as order_item.cancelled_by_user_id/_name (V35) — no FK to
-- app_user, same reasoning (survives a later rename/deactivation).

CREATE TABLE discount (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    command_id           UUID NOT NULL REFERENCES command (id),
    type                 VARCHAR(20) NOT NULL
        CHECK (type IN ('FIXED_AMOUNT', 'PERCENTAGE')),
    percentage           NUMERIC(5, 2),
    original_amount      NUMERIC(10, 2) NOT NULL,
    discounted_amount    NUMERIC(10, 2) NOT NULL,
    reason               TEXT,
    applied_by_user_id   UUID NOT NULL,
    applied_by_user_name VARCHAR(255) NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT ck_discount_percentage_scope CHECK (
        (type = 'FIXED_AMOUNT' AND percentage IS NULL)
        OR (type = 'PERCENTAGE' AND percentage IS NOT NULL)
    )
);

CREATE INDEX idx_discount_command_id ON discount (command_id);
