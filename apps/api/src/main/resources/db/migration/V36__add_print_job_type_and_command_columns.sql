-- V36__add_print_job_type_and_command_columns.sql
-- Printing domain (FARELO-210/211): a second kind of PrintJob —
-- COMMAND_CHECK ("conferência", a customer-facing pre-bill for a whole
-- Command) alongside the existing KITCHEN_TICKET (order-scoped, see
-- V15__create_print_job_table.sql). Both still flow through the same
-- PENDING/PRINTED/FAILED queue for the Edge Agent — see PrintJob's javadoc.
--
-- order_id becomes nullable: a COMMAND_CHECK job isn't scoped to one
-- order. command_id is the COMMAND_CHECK counterpart — nullable, since a
-- KITCHEN_TICKET job doesn't use it. type distinguishes the two and is
-- backfilled to KITCHEN_TICKET for every existing row (the only kind that
-- existed before this migration), then the default is dropped so every
-- future insert must state its type explicitly — same
-- backfill-then-drop-default convention already used for command.status
-- historically and order_item.cancel_reason (V35).
--
-- ck_print_job_type_scope enforces the two shapes are mutually exclusive
-- at the database level, not just in application code — same
-- defense-in-depth convention as
-- ck_order_item_other_reason_requires_description (V35).

ALTER TABLE print_job
    ALTER COLUMN order_id DROP NOT NULL,
    ADD COLUMN command_id UUID REFERENCES command (id),
    ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'KITCHEN_TICKET'
        CHECK (type IN ('KITCHEN_TICKET', 'COMMAND_CHECK'));

ALTER TABLE print_job ALTER COLUMN type DROP DEFAULT;

ALTER TABLE print_job
    ADD CONSTRAINT ck_print_job_type_scope CHECK (
        (type = 'KITCHEN_TICKET' AND order_id IS NOT NULL AND command_id IS NULL)
        OR (type = 'COMMAND_CHECK' AND command_id IS NOT NULL AND order_id IS NULL)
    );

CREATE INDEX idx_print_job_command_id ON print_job (command_id);
