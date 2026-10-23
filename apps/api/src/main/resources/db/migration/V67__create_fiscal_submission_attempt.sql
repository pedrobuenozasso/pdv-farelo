-- One durable authorization attempt per fiscal document. A crash after
-- DISPATCHING is uncertain: consult SEFAZ by access key before any action.
CREATE TABLE fiscal_submission_attempt (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL UNIQUE REFERENCES fiscal_document (id),
    access_key VARCHAR(44) NOT NULL UNIQUE,
    request_sha256 CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN
        ('RESERVED', 'DISPATCHING', 'UNCERTAIN', 'RESOLVED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
