-- A command row represents one visit. Keep the existing UUID foreign keys on
-- orders, payments, discounts and fiscal documents so historical ledgers stay
-- attached to their original visit when the physical number is reused.
ALTER TABLE command
    ADD COLUMN cycle_number INTEGER NOT NULL DEFAULT 1
        CHECK (cycle_number > 0);

ALTER TABLE command DROP CONSTRAINT uk_command_number;

ALTER TABLE command
    ADD CONSTRAINT uk_command_number_cycle UNIQUE (number, cycle_number);

-- At most one currently usable row for each physical command number.
CREATE UNIQUE INDEX uk_command_one_current_cycle
    ON command (number) WHERE status <> 'CLOSED';
