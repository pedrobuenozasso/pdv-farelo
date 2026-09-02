-- V10__create_outbox_event_table.sql
-- Cross-cutting infrastructure (FARELO-060), NOT a business domain table —
-- see the "Outbox" section of docs/domain-model.md and docs/architecture.md's
-- "Eventos internos de domínio via Transactional Outbox + Worker, antes de
-- introduzir um broker externo (sem Kafka neste momento)" principle.
--
-- A row here is written by OutboxPublisher in the SAME transaction as the
-- domain write it records (that's what makes it "transactional": either
-- both commit, or neither does), and later drained by OutboxWorker, which
-- polls for PENDING rows.
--
-- status uses the same VARCHAR + CHECK constraint convention as
-- command.status/orders.status (V5__create_command_table.sql /
-- V7__create_order_table.sql).

CREATE TABLE outbox_event (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   UUID NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSED')),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    processed_at   TIMESTAMP WITH TIME ZONE
);

-- The worker polls for PENDING rows (OutboxWorker#processPendingEvents) —
-- this index supports that filter directly.
CREATE INDEX idx_outbox_event_status ON outbox_event (status);
