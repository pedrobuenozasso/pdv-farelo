-- Preserve the per-NFC-e SEFAZ rejection and its original reply for audit.
ALTER TABLE fiscal_document
    ADD COLUMN rejection_code VARCHAR(3),
    ADD COLUMN rejection_reason VARCHAR(255),
    ADD COLUMN rejection_response_xml TEXT;

ALTER TABLE fiscal_document
    ADD CONSTRAINT ck_fiscal_rejection_code
        CHECK (rejection_code IS NULL OR rejection_code ~ '^[0-9]{3}$');
