ALTER TABLE fiscal_profile
    ADD COLUMN icms_origin VARCHAR(1),
    ADD COLUMN cest VARCHAR(7),
    ADD COLUMN pis_cst VARCHAR(2),
    ADD COLUMN cofins_cst VARCHAR(2),
    ADD CONSTRAINT ck_fiscal_profile_icms_origin
        CHECK (icms_origin IS NULL OR icms_origin ~ '^[0-8]$'),
    ADD CONSTRAINT ck_fiscal_profile_cest
        CHECK (cest IS NULL OR cest ~ '^[0-9]{7}$'),
    ADD CONSTRAINT ck_fiscal_profile_pis_cst
        CHECK (pis_cst IS NULL OR pis_cst IN ('04', '06', '07', '08', '09', '99')),
    ADD CONSTRAINT ck_fiscal_profile_cofins_cst
        CHECK (cofins_cst IS NULL OR cofins_cst IN ('04', '06', '07', '08', '09', '99'));
