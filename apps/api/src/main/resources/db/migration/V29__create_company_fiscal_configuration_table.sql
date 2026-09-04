-- V29__create_company_fiscal_configuration_table.sql
-- Fiscal domain (FARELO-155): second entity of the fiscal domain (prompt
-- mestre seção 23, Epic 11: "NÃO EMITIR NFC-e ainda"). Distinct from
-- fiscal_profile (V27) — this table represents the business's OWN fiscal
-- identity (the company that will one day issue NFC-e), not a per-product
-- tax classification.
--
-- Columns: cnpj/legal_name (required — the bare minimum needed to identify
-- "which company" for fiscal purposes) and trade_name/state_registration/
-- address (optional — standard company-registration identity data). None
-- of these are literally named in prompt mestre seção 24 (that section's
-- field list — NCM/CFOP/CST/CSOSN/CEST/origem/cBenef/ICMS/CBS/IBS — is
-- per-product tax-classification data, FiscalProfile's territory, not
-- company identity). See CompanyFiscalConfiguration's javadoc and
-- docs/domain-model.md's fiscal section (FARELO-155 subsection) for the
-- full field-sourcing rationale, including why a tax-regime/Simples
-- Nacional indicator was deliberately left out.
--
-- Deliberately NO uniqueness constraint / fixed id enforcing "only one
-- row" at the database level — see CompanyFiscalConfiguration's javadoc
-- for the singleton-shape reasoning (option (a): ordinary table, the
-- single-row invariant is kept by the API surface — no POST, PUT always
-- targets the one existing row if any — not by a DB constraint).
--
-- Same column-shape conventions as fiscal_profile (V27)/product (V3):
-- created_at/updated_at are UTC, address is TEXT (nullable free text,
-- same shape as product.description) rather than a structured multi-column
-- address — no consumer needs structured address yet (NFC-e emission is
-- Epic 12, not started, and gated on accounting validation).

CREATE TABLE company_fiscal_configuration (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cnpj                VARCHAR(32) NOT NULL,
    legal_name          VARCHAR(120) NOT NULL,
    trade_name          VARCHAR(120),
    state_registration  VARCHAR(32),
    address             TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
