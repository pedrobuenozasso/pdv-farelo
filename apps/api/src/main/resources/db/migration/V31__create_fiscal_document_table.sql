-- V31__create_fiscal_document_table.sql
-- Fiscal domain (FARELO-156): a durable record representing a fiscal
-- document (an NFC-e, once Epic 12 eventually emits real ones) associated
-- with a comanda/sale. See FiscalDocument's javadoc for the full design
-- reasoning, including why this is a plain data-holding table with no
-- emission logic — Epic 11 explicitly says "NÃO EMITIR NFC-e ainda", and
-- Epic 12 (real emission) is gated on accounting validation.
--
-- command_id: NOT NULL FK to command(id), mirrors FiscalDocument's required
-- @ManyToOne — same relationship shape as payment.command_id (V26). No
-- FK/column added to command — the relationship stays unidirectional, same
-- as orders.command_id (V7) / payment.command_id (V26).
--
-- status: VARCHAR + CHECK, same convention as payment.method (V26) /
-- inventory_movement.type (V21) / command.status (V5), mirroring
-- FiscalDocumentStatus's full six-value enum verbatim from the prompt
-- mestre (seção 25, NFC-e). Defaults to 'PENDING' ("not yet emitted").
--
-- document_number / series / access_key / protocol_number / xml_content /
-- authorized_at: all nullable placeholders for what a real NFC-e eventually
-- carries (prompt mestre seções 24-25) — none of them are populated by this
-- ticket, since nothing here computes/validates/transmits any of them (that
-- is Epic 12, FARELO-170+). access_key holds the 44-digit "chave de
-- acesso" as a plain, unvalidated string (VARCHAR(44), no digit-format
-- check — the prompt mestre doesn't define one). xml_content is TEXT, same
-- unstructured-blob convention as product.description /
-- company_fiscal_configuration.address.
--
-- Unlike payment (V26, append-only ledger, every column NOT NULL/
-- updatable=false), this table's row is deliberately mutable: it starts as
-- a placeholder and a future emission process fills it in over time — see
-- FiscalDocument's javadoc, "Deliberately mutable, unlike Payment". Hence
-- updated_at exists here (same shape as print_job.updated_at, V15), unlike
-- payment.

CREATE TABLE fiscal_document (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    command_id       UUID NOT NULL REFERENCES command (id),
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'AUTHORIZED', 'REJECTED', 'CANCELLED', 'CONTINGENCY')),
    document_number  INTEGER,
    series           INTEGER,
    access_key       VARCHAR(44),
    protocol_number  VARCHAR(64),
    xml_content      TEXT,
    authorized_at    TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Supports GET /api/v1/commands/{number}/fiscal-documents
-- (FiscalDocumentRepository#findByCommandOrderByCreatedAtAsc).
CREATE INDEX idx_fiscal_document_command_id ON fiscal_document (command_id);
