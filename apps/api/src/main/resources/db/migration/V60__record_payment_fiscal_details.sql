-- Nullable for historical payments. Fiscal emission must reject electronic
-- payments without evidence rather than infer a value from the method.
ALTER TABLE payment
    ADD COLUMN fiscal_integration_type VARCHAR(1),
    ADD COLUMN fiscal_institution_cnpj VARCHAR(14),
    ADD COLUMN fiscal_card_brand VARCHAR(2),
    ADD COLUMN fiscal_authorization_code VARCHAR(128),
    ADD COLUMN fiscal_receiver_cnpj VARCHAR(14),
    ADD COLUMN fiscal_terminal_id VARCHAR(40);

ALTER TABLE payment ADD CONSTRAINT ck_payment_fiscal_details CHECK (
    (fiscal_integration_type IS NULL AND fiscal_institution_cnpj IS NULL
        AND fiscal_card_brand IS NULL AND fiscal_authorization_code IS NULL
        AND fiscal_receiver_cnpj IS NULL AND fiscal_terminal_id IS NULL)
    OR (method IN ('PIX', 'CREDIT_CARD', 'DEBIT_CARD')
        AND fiscal_integration_type IS NOT NULL
        AND fiscal_integration_type IN ('1', '2')
        AND (fiscal_integration_type <> '1' OR
            (fiscal_institution_cnpj IS NOT NULL AND fiscal_authorization_code IS NOT NULL)))
);
