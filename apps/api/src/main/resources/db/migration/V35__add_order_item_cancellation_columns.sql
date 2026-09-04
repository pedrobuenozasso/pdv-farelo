-- V35__add_order_item_cancellation_columns.sql
-- Novos tickets FARELO-200/201 ("Cancelar item individual" +
-- "Motivo obrigatório no cancelamento"). Order já tinha um status
-- CANCELLED (FARELO-050/057-058) para o pedido inteiro; OrderItem não
-- tinha nenhum jeito de marcar UM item como cancelado sem cancelar todo
-- o pedido — este ticket adiciona exatamente isso.
--
-- "item não deve ser apagado; marcar como cancelado" (requisito do
-- FARELO-200): por isso `cancelled_at` (nullable), não um DELETE — o
-- mesmo "soft" pattern já usado em toda parte deste schema (Ingredient/
-- Recipe/Category .active, nunca linhas apagadas). `cancelled_at IS NOT
-- NULL` é o próprio "está cancelado", sem precisar de uma coluna boolean
-- redundante.
--
-- "registrar data/hora; registrar operador": cancelled_at cobre a
-- data/hora; cancelled_by_user_id + cancelled_by_user_name cobrem o
-- operador — mesmo par id+nome desnormalizado que AuditLog.userName já
-- usa (V25), pela mesma razão: preservar quem cancelou mesmo que a conta
-- do usuário seja renomeada ou desativada depois. Sem FK pra app_user,
-- de propósito, mesma decisão de AuditLog.
--
-- cancel_reason (FARELO-201): um dos 5 valores fixos do ticket
-- (CUSTOMER_REQUEST/ENTRY_ERROR/OUT_OF_STOCK/QUALITY_ISSUE/OTHER) — ver
-- OrderItemCancelReason.java. cancel_description é obrigatório apenas
-- quando cancel_reason = OTHER ("Se for OTHER, exigir descrição") — já
-- validado na borda do DTO (OrderItemCancelRequest), e reforçado aqui
-- como defesa em profundidade, mesmo padrão "DTO + CHECK" já usado em
-- todo o resto deste schema.
--
-- Deliberadamente FORA de escopo deste ticket (fica para FARELO-203,
-- "Reverter estoque ao cancelar item", ticket futuro e distinto): nenhuma
-- movimentação de estoque é revertida automaticamente quando um item é
-- cancelado aqui — um item cujo produto tinha receita ativa já consumiu
-- ingredientes (InventoryMovementService#consumeForOrder,
-- ORDER_CONSUMPTION) no momento da criação do pedido, e esse consumo
-- permanece no ledger sem estorno até o FARELO-203 existir. Ver o
-- javadoc de OrderService#cancelItem para o aviso completo.

ALTER TABLE order_item
    ADD COLUMN cancelled_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN cancelled_by_user_id UUID,
    ADD COLUMN cancelled_by_user_name VARCHAR(255),
    ADD COLUMN cancel_reason VARCHAR(20)
        CHECK (cancel_reason IN
            ('CUSTOMER_REQUEST', 'ENTRY_ERROR', 'OUT_OF_STOCK', 'QUALITY_ISSUE', 'OTHER')),
    ADD COLUMN cancel_description TEXT,
    ADD CONSTRAINT ck_order_item_other_reason_requires_description
        CHECK (cancel_reason IS DISTINCT FROM 'OTHER' OR cancel_description IS NOT NULL);
