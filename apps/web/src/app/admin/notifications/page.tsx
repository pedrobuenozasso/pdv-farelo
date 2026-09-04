"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { AdminShell } from "@/components/admin-shell";
import { AuthGuard } from "@/components/auth-guard";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { apiErrorMessage } from "@/lib/api/client";
import {
  listNotifications,
  sendNotification,
  type Notification,
  type NotificationStatus,
  type NotificationType,
} from "@/lib/api/notifications";
import { cn } from "@/lib/cn";

const TYPE_LABEL: Record<NotificationType, string> = {
  ORDER_READY: "Pedido pronto",
  STOCK_LOW: "Estoque baixo",
  STOCK_CRITICAL: "Estoque crítico",
  OUT_OF_STOCK: "Sem estoque",
  PRINT_FAILED: "Falha na impressão",
};

const STATUS_LABEL: Record<NotificationStatus, string> = {
  PENDING: "Pendente",
  SENT: "Enviada",
  FAILED: "Falhou",
};

const STATUS_TONE: Record<NotificationStatus, "amber" | "green" | "red"> = {
  PENDING: "amber",
  SENT: "green",
  FAILED: "red",
};

const dateTimeFormatter = new Intl.DateTimeFormat("pt-BR", {
  dateStyle: "short",
  timeStyle: "short",
});

const FILTERS: { label: string; value: NotificationStatus | undefined }[] = [
  { label: "Todas", value: undefined },
  { label: "Pendente", value: "PENDING" },
  { label: "Enviada", value: "SENT" },
  { label: "Falhou", value: "FAILED" },
];

export default function NotificationsAdminPage() {
  const [filter, setFilter] = useState<NotificationStatus | undefined>(
    undefined,
  );

  const notificationsQuery = useQuery({
    queryKey: ["notifications", filter ?? "all"],
    queryFn: () => listNotifications(filter),
  });

  return (
    <AuthGuard>
      <AdminShell>
        <div className="mx-auto flex max-w-4xl flex-col gap-6">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="font-serif text-2xl font-semibold">
                Notificações
              </h1>
              <p className="text-ink-soft mt-0.5 text-sm">
                {notificationsQuery.data?.length ?? 0} notificações
              </p>
            </div>
          </div>

          <div className="flex gap-2">
            {FILTERS.map((option) => (
              <button
                key={option.label}
                type="button"
                onClick={() => setFilter(option.value)}
                className={cn(
                  "rounded-full px-3.5 py-1.5 text-[13px] font-semibold",
                  filter === option.value
                    ? "bg-primary text-primary-ink"
                    : "border-line text-ink-soft border",
                )}
              >
                {option.label}
              </button>
            ))}
          </div>

          <div className="border-line bg-surface overflow-hidden rounded-2xl border">
            {notificationsQuery.isLoading ? (
              <p className="text-ink-faint p-5 text-sm">Carregando...</p>
            ) : null}
            {notificationsQuery.isError ? (
              <p className="text-red p-5 text-sm">
                Não foi possível carregar as notificações.
              </p>
            ) : null}
            {notificationsQuery.data && notificationsQuery.data.length === 0 ? (
              <p className="text-ink-faint p-5 text-sm">
                Nenhuma notificação encontrada.
              </p>
            ) : null}
            {notificationsQuery.data?.map((notification) => (
              <NotificationRow
                key={notification.id}
                notification={notification}
              />
            ))}
          </div>
        </div>
      </AdminShell>
    </AuthGuard>
  );
}

function NotificationRow({ notification }: { notification: Notification }) {
  const queryClient = useQueryClient();

  const sendMutation = useMutation({
    mutationFn: () => sendNotification(notification.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
    },
  });

  const errorMsg = apiErrorMessage(
    sendMutation.error,
    "Não foi possível enviar a notificação.",
  );

  return (
    <div className="border-line flex items-center gap-4 border-t px-5 py-3.5 first:border-t-0">
      <div className="flex-1">
        <div className="flex items-center gap-2">
          <span className="text-sm font-semibold">
            {TYPE_LABEL[notification.type]}
          </span>
          <span className="text-ink-faint text-xs">
            para {notification.recipient}
          </span>
        </div>
        <p className="text-ink-soft mt-0.5 line-clamp-1 text-sm">
          {notification.content}
        </p>
        <div className="text-ink-faint mt-1 text-xs">
          {dateTimeFormatter.format(new Date(notification.createdAt))}
        </div>
        {errorMsg ? <p className="text-red mt-1 text-xs">{errorMsg}</p> : null}
      </div>
      <Badge tone={STATUS_TONE[notification.status]}>
        {STATUS_LABEL[notification.status]}
      </Badge>
      <Button
        variant="outline"
        disabled={sendMutation.isPending}
        onClick={() => sendMutation.mutate()}
        className="px-4 py-2 text-[13px]"
      >
        {sendMutation.isPending
          ? "Enviando..."
          : notification.status === "PENDING"
            ? "Enviar"
            : "Reenviar"}
      </Button>
    </div>
  );
}
