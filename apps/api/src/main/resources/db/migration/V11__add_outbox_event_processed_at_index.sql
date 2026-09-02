-- V11__add_outbox_event_processed_at_index.sql
-- FARELO-061: supports OutboxRetentionCleaner's cleanup query
-- (OutboxEventRepository#deleteByStatusAndProcessedAtBefore), which filters
-- on status = 'PROCESSED' AND processed_at < :cutoff. A composite index on
-- (status, processed_at) lets that query use the index for both the status
-- filter and the processed_at range, rather than falling back to
-- idx_outbox_event_status (V10) and then scanning every PROCESSED row.
--
-- idx_outbox_event_status (V10) is kept as-is — it backs OutboxWorker's
-- PENDING poll, a different query this index doesn't need to serve.

CREATE INDEX idx_outbox_event_status_processed_at ON outbox_event (status, processed_at);
