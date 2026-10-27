-- FISC-09d: a closed visit has at most one fiscal document. The service
-- already locks the command before starting a placeholder, but other writers
-- must not be able to bypass that invariant. The unique index also serves
-- the command-scoped lookup; no duplicate non-unique index is needed.
-- Existing duplicates cause this migration to fail for manual review rather
-- than silently deleting fiscal evidence.
CREATE UNIQUE INDEX uq_fiscal_document_command_id ON fiscal_document (command_id);
DROP INDEX idx_fiscal_document_command_id;
