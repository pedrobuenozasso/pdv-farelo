-- V22__create_notification_table.sql
-- Notification domain (FARELO-110): a durable record of something that
-- needs to be (or already was) sent to a recipient — today, in practice,
-- always a WhatsApp message (prompt mestre seção 19: "Utilizar futuramente:
-- Meta WhatsApp Cloud API. Fluxo: ORDER_READY → Notification Worker →
-- WhatsApp. Notificações internas também poderão existir: estoque baixo,
-- estoque zerado, falha de impressão."). See Notification's javadoc for the
-- full design rationale (why this is a standalone table with no FK to
-- outbox_event, why recipient has no separate channel column, why content
-- is plain text rather than jsonb).
--
-- type/status use the same VARCHAR + CHECK constraint convention as
-- command.status/orders.status/print_job.status/outbox_event.status.

CREATE TABLE notification (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type       VARCHAR(30) NOT NULL
        CHECK (type IN ('ORDER_READY', 'STOCK_LOW', 'STOCK_CRITICAL', 'OUT_OF_STOCK', 'PRINT_FAILED')),
    recipient  VARCHAR(32) NOT NULL,
    content    TEXT NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Supports NotificationRepository#findByStatusOrderByCreatedAtAsc — a
-- future worker (FARELO-112/113) polling for PENDING rows, same shape as
-- outbox_event/print_job's own status index.
CREATE INDEX idx_notification_status ON notification (status);
