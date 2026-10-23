-- Existing fiscal rows may have series/number without issuer/environment;
-- retain them for review. New reservations always fill the complete scope.
ALTER TABLE fiscal_document
    ADD COLUMN issuer_cnpj VARCHAR(14),
    ADD COLUMN fiscal_environment VARCHAR(12);

ALTER TABLE fiscal_document
    ADD CONSTRAINT chk_fiscal_document_issuer_cnpj
        CHECK (issuer_cnpj IS NULL OR issuer_cnpj ~ '^[0-9]{14}$'),
    ADD CONSTRAINT chk_fiscal_document_environment
        CHECK (fiscal_environment IS NULL OR fiscal_environment IN ('HOMOLOGATION', 'PRODUCTION'));

CREATE UNIQUE INDEX uq_fiscal_document_number_scope
    ON fiscal_document (issuer_cnpj, fiscal_environment, series, document_number)
    WHERE issuer_cnpj IS NOT NULL AND fiscal_environment IS NOT NULL
      AND series IS NOT NULL AND document_number IS NOT NULL;

-- No rows are seeded. The first next_number must be explicitly reconciled
-- with the issuer's prior numbering before any service can reserve it.
CREATE TABLE fiscal_number_sequence (
    id UUID PRIMARY KEY,
    issuer_cnpj VARCHAR(14) NOT NULL CHECK (issuer_cnpj ~ '^[0-9]{14}$'),
    environment VARCHAR(12) NOT NULL CHECK (environment IN ('HOMOLOGATION', 'PRODUCTION')),
    series INTEGER NOT NULL CHECK (series BETWEEN 0 AND 999),
    next_number BIGINT NOT NULL CHECK (next_number BETWEEN 1 AND 1000000000),
    CONSTRAINT uq_fiscal_number_sequence_scope UNIQUE (issuer_cnpj, environment, series)
);
