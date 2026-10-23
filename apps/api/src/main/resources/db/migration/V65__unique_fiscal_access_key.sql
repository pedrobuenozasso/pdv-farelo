-- A SEFAZ access key identifies one NFC-e across issuer, environment, series
-- and number. Nullable PENDING placeholders can coexist until assigned.
-- Do not add a unique(series, document_number) constraint: the schema has no
-- issuer/environment columns yet and those numbers can legitimately repeat.
-- Existing duplicate non-null keys deliberately fail migration for review;
-- never pick a winner or rewrite fiscal evidence automatically.
CREATE UNIQUE INDEX uq_fiscal_document_access_key
    ON fiscal_document (access_key)
    WHERE access_key IS NOT NULL;
