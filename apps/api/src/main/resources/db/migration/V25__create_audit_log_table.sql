-- V25__create_audit_log_table.sql
-- Audit domain (FARELO-125): a durable, append-only record of sensitive
-- operations (prompt mestre seção 27: "Operações sensíveis precisam
-- registrar: quem, quando, o quê, valor anterior, valor novo"; seção 26
-- lists "audit log" among the mandatory security items). See AuditLog's
-- javadoc for the full design rationale.
--
-- Append-only, same convention as inventory_movement (V21): no UPDATE/
-- DELETE is ever issued against this table by application code, every
-- column is NOT NULL except previous_value/new_value, and there is no
-- updated_at column — an audit record has no "later state" to track, only
-- the fact that this happened.
--
-- user_id/user_name/user_email: a snapshot of the acting user captured by
-- value at the moment of the action — deliberately NOT a foreign key to
-- app_user(id) (contrast with inventory_movement.order_id in V21, which
-- does keep a plain DB-level FK to orders(id) for referential integrity,
-- because that column has no human-readable snapshot of its own to fall
-- back on). Here, user_name/user_email already make every row
-- self-describing independent of the app_user row's continued existence —
-- an FK would buy nothing beyond what's already captured by value, while
-- risking the audit trail being blocked by a future user-deletion feature
-- (none exists today, only app_user.active), which is backwards for what
-- this table is for.
--
-- action/entity_type: plain VARCHAR, deliberately no CHECK constraint —
-- unlike notification.type (V22) / inventory_movement.type (V21), which
-- mirror closed enums given in full by the prompt mestre upfront, seção 27
-- only gives illustrative examples ("Principalmente: preço, estoque,
-- cancelamento, pagamento, configuração fiscal, produto"), not an exhaustive
-- list. The real action/entity-type vocabulary is defined incrementally by
-- future producers (FARELO-126, FARELO-127, and beyond) this ticket has no
-- visibility into — a CHECK constraint here would need a migration for
-- every new one. entity_type is expected to be a simple entity name (e.g.
-- "Product", "Ingredient") by convention, not enforced by this schema.
--
-- previous_value/new_value: JSONB, nullable (e.g. a creation has no
-- meaningful "before" state) — opaque to this ticket, same "structured
-- snapshot, no opinion on shape" convention already used by
-- print_job.content (V15) / outbox_event.payload (V10).

CREATE TABLE audit_log (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL,
    user_name      VARCHAR(120) NOT NULL,
    user_email     VARCHAR(160) NOT NULL,
    action         VARCHAR(60) NOT NULL,
    entity_type    VARCHAR(60) NOT NULL,
    entity_id      UUID NOT NULL,
    previous_value JSONB,
    new_value      JSONB,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Supports AuditLogRepository#findByEntityTypeAndEntityIdOrderByCreatedAtDesc
-- (GET /api/v1/audit-logs?entityType=&entityId=) — the audit trail of one
-- specific record, the single most obvious real query an audit log exists
-- to answer.
CREATE INDEX idx_audit_log_entity ON audit_log (entity_type, entity_id);

-- Supports AuditLogRepository#findByUserIdOrderByCreatedAtDesc
-- (GET /api/v1/audit-logs?userId=) — everything one user did.
CREATE INDEX idx_audit_log_user_id ON audit_log (user_id);
