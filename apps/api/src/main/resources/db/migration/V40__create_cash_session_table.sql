-- V40__create_cash_session_table.sql
-- CashSession domain (FARELO-307/308/309, "Caixa"): a cash-drawer session
-- opened against a Shift template by an operator. Unlike payment/discount/
-- audit_log (append-only ledgers), this is a mutable, stateful entity with
-- a real lifecycle (open -> closed -> reconciled) — same shape as command/
-- orders, not those ledgers.
--
-- Full column set declared now (FARELO-307), even though the closing
-- columns are only ever written by FARELO-308 and the reconciliation
-- columns only by FARELO-309: closing and reconciliation are two tightly
-- coupled facets of one entity's single lifecycle, not independently
-- arriving attributes (contrast fiscal_profile's incremental V30/V32/V33).
-- Same precedent as command.status (V5) declaring its complete 5-value
-- CHECK from the first ticket even though CLOSED/BLOCKED transitions
-- arrived only in later tickets.
--
-- shift_id: NOT NULL FK to shift(id), mirrors CashSession's required
-- @ManyToOne, fixed at open time (no UPDATE ever changes it).
--
-- opened_by_user_id/opened_by_user_name, closed_by_user_id/closed_by_user_name,
-- reconciled_by_user_id/reconciled_by_user_name: denormalized id+name pairs,
-- no FK to app_user — same convention as discount.applied_by_user_id/_name
-- (V37) and order_item.cancelled_by_user_id/_name (V35): survives a later
-- rename/deactivation of the user.
--
-- ck_cash_session_closed_scope / ck_cash_session_reconciliation_scope:
-- defense-in-depth exclusivity checks, same style as
-- ck_discount_percentage_scope (V37) — the four closing columns (and,
-- separately, the five reconciliation columns) are either all NULL or all
-- NOT NULL together, never a partial mix.

CREATE TABLE cash_session (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shift_id                   UUID NOT NULL REFERENCES shift (id),
    opened_by_user_id          UUID NOT NULL,
    opened_by_user_name        VARCHAR(255) NOT NULL,
    opened_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    status                     VARCHAR(20) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'CLOSED')),
    closing_amount             NUMERIC(10, 2),
    closed_at                  TIMESTAMP WITH TIME ZONE,
    closed_by_user_id          UUID,
    closed_by_user_name        VARCHAR(255),
    reconciliation_status      VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (reconciliation_status IN ('PENDING', 'MATCHED', 'MISMATCHED')),
    expected_amount            NUMERIC(10, 2),
    reconciliation_difference  NUMERIC(10, 2),
    reconciled_at              TIMESTAMP WITH TIME ZONE,
    reconciled_by_user_id      UUID,
    reconciled_by_user_name    VARCHAR(255),
    created_at                 TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT ck_cash_session_closed_scope CHECK (
        (status = 'OPEN' AND closing_amount IS NULL AND closed_at IS NULL
            AND closed_by_user_id IS NULL AND closed_by_user_name IS NULL)
        OR (status = 'CLOSED' AND closing_amount IS NOT NULL AND closed_at IS NOT NULL
            AND closed_by_user_id IS NOT NULL AND closed_by_user_name IS NOT NULL)
    ),
    CONSTRAINT ck_cash_session_reconciliation_scope CHECK (
        (reconciliation_status = 'PENDING' AND expected_amount IS NULL
            AND reconciliation_difference IS NULL AND reconciled_at IS NULL
            AND reconciled_by_user_id IS NULL AND reconciled_by_user_name IS NULL)
        OR (reconciliation_status IN ('MATCHED', 'MISMATCHED') AND expected_amount IS NOT NULL
            AND reconciliation_difference IS NOT NULL AND reconciled_at IS NOT NULL
            AND reconciled_by_user_id IS NOT NULL AND reconciled_by_user_name IS NOT NULL)
    )
);

-- Supports CashSessionService#open's "one open session per operator" check
-- (CashSessionRepository#findByOpenedByUserIdAndStatus).
CREATE INDEX idx_cash_session_opened_by_user_status ON cash_session (opened_by_user_id, status);
CREATE INDEX idx_cash_session_shift_id ON cash_session (shift_id);
