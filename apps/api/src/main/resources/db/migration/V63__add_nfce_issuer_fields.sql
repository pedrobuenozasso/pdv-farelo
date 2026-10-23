-- Explicit issuer data required by the NFC-e XML. Existing rows remain
-- incomplete until reviewed and filled by an administrator.
ALTER TABLE company_fiscal_configuration
    ADD COLUMN tax_regime VARCHAR(1),
    ADD COLUMN street VARCHAR(60),
    ADD COLUMN street_number VARCHAR(60),
    ADD COLUMN district VARCHAR(60),
    ADD COLUMN city_code VARCHAR(7),
    ADD COLUMN city VARCHAR(60),
    ADD COLUMN postal_code VARCHAR(8);

ALTER TABLE company_fiscal_configuration
    ADD CONSTRAINT ck_company_fiscal_tax_regime
        CHECK (tax_regime IS NULL OR tax_regime IN ('1', '3')),
    ADD CONSTRAINT ck_company_fiscal_city_code
        CHECK (city_code IS NULL OR city_code ~ '^[0-9]{7}$'),
    ADD CONSTRAINT ck_company_fiscal_postal_code
        CHECK (postal_code IS NULL OR postal_code ~ '^[0-9]{8}$');
