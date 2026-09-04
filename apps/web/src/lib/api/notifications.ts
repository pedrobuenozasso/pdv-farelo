// Thin client for /api/v1/notifications (see docs/api.md, Epic 8).
// Client-only — called from app/admin/notifications/page.tsx.

import { authHeaders, parseResponse } from "./client";

export type NotificationType =
  | "ORDER_READY"
  | "STOCK_LOW"
  | "STOCK_CRITICAL"
  | "OUT_OF_STOCK"
  | "PRINT_FAILED";

export type NotificationStatus = "PENDING" | "SENT" | "FAILED";

export type Notification = {
  id: string;
  type: NotificationType;
  recipient: string;
  content: string;
  status: NotificationStatus;
  createdAt: string;
  updatedAt: string;
};

export async function listNotifications(
  status?: NotificationStatus,
): Promise<Notification[]> {
  const url = status
    ? `/api/v1/notifications?status=${status}`
    : "/api/v1/notifications";
  const response = await fetch(url, {
    cache: "no-store",
    headers: authHeaders(),
  });
  return parseResponse<Notification[]>(response);
}

// Sem checagem de status prévio no backend — chamável mesmo em
// PENDING/SENT/FAILED (reenvio manual). Sempre 200, mesmo quando o envio
// real falha (o resultado vem no `status` da resposta, não num erro HTTP).
export async function sendNotification(id: string): Promise<Notification> {
  const response = await fetch(`/api/v1/notifications/${id}/send`, {
    method: "POST",
    headers: authHeaders(),
  });
  return parseResponse<Notification>(response);
}
