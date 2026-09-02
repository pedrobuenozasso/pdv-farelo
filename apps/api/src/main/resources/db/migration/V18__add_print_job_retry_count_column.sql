-- V18__add_print_job_retry_count_column.sql
-- Printing domain (FARELO-079): retry mechanism for FAILED print jobs. See
-- PrintJob#retry()/PrintJobService#retry(UUID) for the full design
-- rationale (manual retry via POST /api/v1/print-jobs/{id}/retry, capped at
-- PrintJobService.MAX_RETRY_COUNT attempts).
--
-- retry_count tracks how many times a job has been moved back from FAILED
-- to PENDING — starts at 0 for every job (including every row that already
-- exists), incremented by PrintJob#retry() on each successful retry. The
-- application layer (PrintJobService), not a CHECK constraint, enforces the
-- maximum: the limit is a business policy that may change independently of
-- the schema, same reasoning already applied to status transition rules
-- (never encoded as a DB constraint) elsewhere in this domain.

ALTER TABLE print_job
    ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0
        CHECK (retry_count >= 0);
