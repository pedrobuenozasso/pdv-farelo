-- V5__create_command_table.sql
-- Command domain (FARELO-030): Command ("comanda"), first table of Epic 2.
-- No seed data yet (1-100 seed is FARELO-031) and no other business rules.

-- status stored as VARCHAR + CHECK constraint (not just VARCHAR) for extra
-- rigidity at the DB level, mirroring the Java enum's allowed values one by
-- one instead of relying solely on application-level validation. Trade-off:
-- adding a new CommandStatus value later requires a follow-up migration to
-- extend this constraint — acceptable given how central command status is
-- to the domain's correctness.
CREATE TABLE command (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    number     INTEGER NOT NULL,
    status     VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE'
        CHECK (status IN ('AVAILABLE', 'OPEN', 'PAYMENT_REQUESTED', 'CLOSED', 'BLOCKED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uk_command_number UNIQUE (number)
);
