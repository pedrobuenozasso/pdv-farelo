-- V26__create_payment_table.sql
-- Payment domain (FARELO-140): a payment recorded against a comanda
-- (command), not against an individual order — see Payment's javadoc for
-- why (prompt mestre seção 30, and Epic 10's own roadmap: FARELO-142
-- "Permitir múltiplos pagamentos por comanda" / FARELO-143 "Validar total
-- pago antes de fechar" only make sense summed per comanda).
--
-- No UPDATE/DELETE is ever issued against this table by application code
-- (see Payment's javadoc, "no status field; append-only, like
-- InventoryMovement/AuditLog") — every column is NOT NULL, no soft-delete/
-- status flag, because there is no "later state" for an already-written
-- row to be in. No updated_at column either, same reasoning as
-- inventory_movement (V21) / audit_log (V25).
--
-- command_id: NOT NULL FK to command(id), mirrors Payment's required
-- @ManyToOne. No FK/column added to command — the relationship stays
-- unidirectional, same as orders.command_id (V7) needed no matching change
-- to the command table.
--
-- amount: NUMERIC(10,2) — same money convention as product.price (V3) /
-- order_item.unit_price (V8), never a floating-point type (AGENTS.md).
--
-- method: VARCHAR + CHECK, same convention as ingredient.unit (V16) /
-- product.production_station (V14) / command.status (V5) / orders.status
-- (V7) / inventory_movement.type (V21), mirroring PaymentMethod's full
-- five-value enum verbatim from the prompt mestre (seção 47, FARELO-141).

CREATE TABLE payment (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    command_id UUID NOT NULL REFERENCES command (id),
    amount     NUMERIC(10, 2) NOT NULL,
    method     VARCHAR(20) NOT NULL
        CHECK (method IN ('PIX', 'CREDIT_CARD', 'DEBIT_CARD', 'CASH', 'OTHER')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Supports GET /api/v1/commands/{number}/payments
-- (PaymentRepository#findByCommandOrderByCreatedAtAsc), and the future
-- "total paid for this comanda" aggregate query FARELO-142/143 will add.
CREATE INDEX idx_payment_command_id ON payment (command_id);
