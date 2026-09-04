-- V33__add_fiscal_profile_cst_csosn_columns.sql
-- Fiscal domain (FARELO-154, "Adicionar CST/CSOSN", prompt mestre seção 24:
-- "Perfis fiscais devem permitir futuramente: NCM, CFOP, CST/CSOSN, CEST,
-- origem, cBenef, ICMS, CBS, IBS"). Adds the CST/CSOSN columns that
-- fiscal_profile (V27) deliberately left out until a concrete ticket needed
-- them — see that migration's own comment ("Deliberately no
-- NCM/CFOP/CST/CSOSN ... column yet ... FARELO-152/153/154"). Third and
-- last of the three fiscal codes named as their own tickets.
--
-- UNLIKE V30 (NCM)/V32 (CFOP), this is not "the same shape, different
-- regex" — CST and CSOSN are two different, mutually exclusive codes, not
-- one code with one format:
--   * CST (Código de Situação Tributária) applies to a business under
--     "Regime Normal" (Lucro Real/Presumido).
--   * CSOSN (Código de Situação da Operação no Simples Nacional) applies to
--     a business under "Simples Nacional".
-- A single fiscal profile is configured for one tax regime, so it uses
-- EITHER cst OR csosn, never both. See FiscalProfile's javadoc (FARELO-154
-- section) for the full reasoning on why this is modeled as two nullable
-- columns rather than one, and docs/domain-model.md's FARELO-154 subsection
-- for the complete decision writeup.
--
-- cst VARCHAR(3), CHECK (cst IS NULL OR cst ~ '^[0-9]{2,3}$'): CST tables
-- differ by tax type (ICMS CST, Tabela A, is 2 digits; IPI/PIS/COFINS CST
-- tables are also commonly 2 digits) and this column doesn't track which
-- tax type it's for, so a single exact-digit-count regex (the way NCM/CFOP
-- got one) would overstate a precision this ticket doesn't actually have.
-- 2-to-3 digits, numeric only, is the loosest honest structural constraint:
-- it rejects obvious garbage (letters, empty-but-not-null, absurd lengths)
-- without pretending to enforce a single canonical CST table.
--
-- csosn VARCHAR(3), CHECK (csosn IS NULL OR csosn ~ '^[0-9]{3}$'): CSOSN,
-- unlike CST, is a single national table (Simples Nacional, Convênio ICMS
-- 123/2012, Anexo I/II) with codes that are always exactly 3 numeric digits
-- (e.g. 101, 102, 103, 201, 202, 203, 300, 400, 500, 900) — same
-- "structurally fixed, not app-specific" confidence as NCM (8 digits, V30)
-- and CFOP (4 digits, V32), so this column gets the same tight exact-length
-- regex those got, unlike cst above.
--
-- Mutual exclusivity: ck_fiscal_profile_cst_csosn_exclusive enforces "at
-- most one of cst/csosn is set" at the DB level (defense-in-depth backstop
-- for any write path that bypasses the DTO layer's own check — see
-- FiscalProfileRequest/FiscalProfileUpdateRequest). Both NULL is allowed
-- (neither configured yet, e.g. right after FiscalProfile creation before
-- an Admin has picked the profile's tax regime downstream) — this
-- constraint only rules out BOTH being non-null at once, it does not
-- require exactly one to be set.
--
-- No tax-regime column added to fiscal_profile to "fully" validate
-- cst-vs-csosn correctness against a regime — prompt mestre never names
-- "Simples Nacional"/"Regime Normal"/CRT, the same absence that made
-- CompanyFiscalConfiguration (FARELO-155, V29) explicitly decline a regime
-- field too. This CHECK constraint is therefore the most this ticket can
-- honestly enforce structurally: mutual exclusivity, not regime-correctness.

ALTER TABLE fiscal_profile
    ADD COLUMN cst VARCHAR(3)
        CHECK (cst IS NULL OR cst ~ '^[0-9]{2,3}$'),
    ADD COLUMN csosn VARCHAR(3)
        CHECK (csosn IS NULL OR csosn ~ '^[0-9]{3}$'),
    ADD CONSTRAINT ck_fiscal_profile_cst_csosn_exclusive
        CHECK (cst IS NULL OR csosn IS NULL);
