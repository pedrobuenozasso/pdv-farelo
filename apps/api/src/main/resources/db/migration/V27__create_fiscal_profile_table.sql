-- V27__create_fiscal_profile_table.sql
-- Fiscal domain (FARELO-150): first table of the fiscal domain (prompt
-- mestre seção 23/24, Epic 11). FiscalProfile is a reusable tax/fiscal
-- classification (e.g. "Isento", "Tributado padrão") that a Product will
-- later be associated with (FARELO-151, a future ticket, not this one).
--
-- Deliberately no NCM/CFOP/CST/CSOSN (or any other seção 24 fiscal code:
-- CEST, origem, cBenef, ICMS, CBS, IBS) column yet — the roadmap names
-- three of those as their own explicit, later-numbered tickets
-- (FARELO-152/153/154), the same "don't anticipate a future ticket's
-- field" discipline already applied to e.g. ingredient.minimum_stock
-- (added only at FARELO-099, not V16). See FiscalProfile's javadoc for the
-- full reasoning.
--
-- Same column shape/conventions as category (V2)/ingredient (V16):
-- name is required, active defaults to TRUE, created_at/updated_at are
-- UTC. description is new relative to Category (nullable free text, same
-- TEXT type as product.description, V3) — a fiscal profile's short name
-- alone often isn't enough to remember which real-world products belong
-- under it later.

CREATE TABLE fiscal_profile (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(120) NOT NULL,
    description TEXT,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
