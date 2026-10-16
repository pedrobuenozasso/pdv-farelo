-- Existing payments have no reliable cash-session identity, and existing
-- sessions have no recorded float. Keep both new fields nullable for history;
-- the API requires them for new writes. Never infer a link from timestamps.
ALTER TABLE cash_session
    ADD COLUMN opening_amount NUMERIC(10, 2)
        CHECK (opening_amount >= 0);

ALTER TABLE payment
    ADD COLUMN cash_session_id UUID REFERENCES cash_session (id);

CREATE INDEX idx_payment_cash_session_method
    ON payment (cash_session_id, method);
