-- Preserve the confirmed cancellation event separately from the authorized NFC-e.
ALTER TABLE fiscal_document
    ADD COLUMN cancellation_protocol_number VARCHAR(64),
    ADD COLUMN cancelled_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN cancellation_event_xml TEXT,
    ADD COLUMN cancellation_response_xml TEXT;

ALTER TABLE fiscal_document
    ADD CONSTRAINT ck_fiscal_cancellation_protocol
        CHECK (cancellation_protocol_number IS NULL
            OR cancellation_protocol_number ~ '^[0-9]{15}([0-9]{2})?$');
