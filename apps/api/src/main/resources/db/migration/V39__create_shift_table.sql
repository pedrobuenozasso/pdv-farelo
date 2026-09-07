-- V39__create_shift_table.sql
-- Shift domain (FARELO-306, "Turno"): a named, reusable template for opening
-- a cash session (Caixa) — e.g. "TURNO 01", pre-configured with an opening
-- account name ("Caixa Dinheiro") and a closing account name ("Tesouraria"),
-- same idea Sischef models but simplified: no real double-entry bookkeeping
-- ledger/account entity exists anywhere in this codebase, so both account
-- names are stored as plain, free-text labels (see Shift's javadoc) rather
-- than a foreign key to something this codebase has no other reason to
-- build. First table of the new `cashier` domain (docs/domain-model.md).
--
-- name is unique (uk_shift_name) — same reasoning as app_user.email (V20):
-- a duplicate-named template ("TURNO 01" twice) would be confusing for
-- staff picking one when opening a cash session, with no legitimate reason
-- to have two.
--
-- active: same soft-deactivation convention as category/product/ingredient/
-- app_user — lets a retired template stop showing up as an option without
-- deleting the historical record any cash_session still references.

CREATE TABLE shift (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                  VARCHAR(255) NOT NULL,
    opening_account_name  VARCHAR(255) NOT NULL,
    closing_account_name  VARCHAR(255) NOT NULL,
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uk_shift_name UNIQUE (name)
);
