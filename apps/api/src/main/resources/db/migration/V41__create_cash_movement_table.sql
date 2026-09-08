-- V41__create_cash_movement_table.sql
-- CashMovement domain (FARELO-310/311): an append-only ledger of ad-hoc
-- cash-drawer movements against a CashSession, not tied to any Order/
-- Command — same shape as payment (V26) / discount (V37): every column
-- NOT NULL where applicable, no status flag, application code never issues
-- UPDATE/DELETE against this table (see CashMovement's javadoc). A wrong
-- entry is corrected by a new offsetting row, never an edit.
--
-- All four CashMovementType values declared now, in FARELO-310's own
-- migration, even though this ticket's producers only cover WITHDRAWAL/
-- SUPPLY (sangria/suprimento) — FARELO-311 (despesa/receita, EXPENSE/
-- INCOME) reuses this exact table/column, no follow-up migration needed.
-- Unlike AuditLog.action (genuinely open-ended, no known vocabulary), the
-- full set here is already given by docs/gaps-sischef-pdv.md before either
-- ticket started — see CashMovement's javadoc for the full reasoning.
--
-- cash_session_id: NOT NULL FK to cash_session(id) — every movement
-- belongs to exactly one cash session, mirrors CashMovement's required
-- @ManyToOne. No FK/column added to cash_session — unidirectional, same
-- convention as payment.command_id (V26) needing no matching column on
-- command.
--
-- amount: NUMERIC(10,2), same money convention as payment.amount (V26) —
-- always a positive magnitude; direction/sign comes from `type`, not the
-- stored value (see CashMovement's javadoc).
--
-- created_by_user_id/created_by_user_name: denormalized id+name pair, no
-- FK to app_user — same convention as discount.applied_by_user_id/_name
-- (V37) and cash_session.opened_by_user_id/_name (V40).

CREATE TABLE cash_movement (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cash_session_id      UUID NOT NULL REFERENCES cash_session (id),
    type                 VARCHAR(20) NOT NULL
        CHECK (type IN ('WITHDRAWAL', 'SUPPLY', 'EXPENSE', 'INCOME')),
    amount               NUMERIC(10, 2) NOT NULL,
    description          TEXT,
    created_by_user_id   UUID NOT NULL,
    created_by_user_name VARCHAR(255) NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Supports GET /api/v1/cash-sessions/{id}/movements
-- (CashMovementRepository#findByCashSessionOrderByCreatedAtAsc).
CREATE INDEX idx_cash_movement_cash_session_id ON cash_movement (cash_session_id);
