-- V1__init.sql
-- Infrastructure migration (FARELO-004): prepares the database for the
-- domain migrations that will follow (Epic 1 — Catálogo, FARELO-010+).
-- No business tables are created here.

-- pgcrypto provides gen_random_uuid(), used as the default for UUID
-- primary keys across the schema. Sortable/time-ordered UUIDv7 values
-- (see AGENTS.md) are generated at the application layer in Java; this
-- extension only covers database-side UUID generation when needed
-- (e.g. column defaults).
CREATE EXTENSION IF NOT EXISTS pgcrypto;
