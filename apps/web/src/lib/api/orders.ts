// Thin client for POST /api/v1/orders (see docs/api.md).
//
// Only called from the cart's client component (menu.tsx, FARELO-045) —
// no Server Component needs it, so this uses a plain relative path
// through the Next.js rewrite in next.config.ts, unlike the isomorphic
// categories.ts/products.ts. Adapt the same way those two did if a
// server-side caller shows up later.
//
// The checkout form collects the customer's name/phone (FARELO-045,
// prompt mestre seção 6) and sends them here as `customerName`/
// `customerPhone` — the backend persists them as a simple snapshot on
// `Order` (no `customer` domain, see docs/domain-model.md's `ordering`
// section and docs/api.md's note on POST /api/v1/orders). Both are
// optional on the wire and nullable on the response.

import { authHeaders, parseResponse, parseVoidResponse } from "./client";

export type OrderStatus =
  "CREATED" | "CONFIRMED" | "PREPARING" | "READY" | "DELIVERED" | "CANCELLED";

// FARELO-312: a walk-up counter sale (BALCAO) has no comanda at all,
// alongside every order until now (COMANDA) — see the backend's
// Order#getType() javadoc ("Design decision — BALCAO").
export type OrderType = "COMANDA" | "BALCAO";

// FARELO-200/201: the five fixed cancellation reasons — must match
// OrderItemCancelReason on the backend exactly (same string values, sent
// as-is in cancelOrderItem's request body).
export type OrderItemCancelReason =
  | "CUSTOMER_REQUEST"
  | "ENTRY_ERROR"
  | "OUT_OF_STOCK"
  | "QUALITY_ISSUE"
  | "OTHER";

export type OrderItem = {
  id: string;
  productId: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  cancelled: boolean;
  cancelledAt: string | null;
  cancelledByUserName: string | null;
  cancelReason: OrderItemCancelReason | null;
  cancelDescription: string | null;
};

export type Order = {
  id: string;
  type: OrderType;
  // FARELO-312: null for a BALCAO order — there is no comanda to name.
  commandNumber: number | null;
  status: OrderStatus;
  items: OrderItem[];
  customerName: string | null;
  customerPhone: string | null;
  // FARELO-314: the waiter/attendant responsible for this order, if any —
  // removable/changeable via assignOrderWaiter below.
  assignedUserId: string | null;
  assignedUserName: string | null;
  // FARELO-316: free-text "Observação do pedido", order-level.
  note: string | null;
  // FARELO-319: "Imprimir ao confirmar" — whether this order's kitchen
  // ticket printed automatically on creation (defaults true on the
  // backend when omitted from the create request).
  printOnConfirm: boolean;
  createdAt: string;
};

export type CreateOrderInput = {
  commandNumber: number;
  items: { productId: string; quantity: number }[];
  customerName?: string;
  customerPhone?: string;
  note?: string;
  printOnConfirm?: boolean;
};

export async function createOrder(input: CreateOrderInput): Promise<Order> {
  const response = await fetch("/api/v1/orders", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return parseResponse<Order>(response);
}

// POST /api/v1/orders/balcao (FARELO-312) — a walk-up counter sale, no
// comanda. Unlike createOrder above, this is a staff-only action (there is
// no customer-facing flow for it), so it requires an Authorization header,
// same as every other write in this file below.
export type CreateBalcaoOrderInput = {
  items: { productId: string; quantity: number }[];
  customerName?: string;
  customerPhone?: string;
  note?: string;
  printOnConfirm?: boolean;
};

export async function createBalcaoOrder(
  input: CreateBalcaoOrderInput,
): Promise<Order> {
  const response = await fetch("/api/v1/orders/balcao", {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify(input),
  });
  return parseResponse<Order>(response);
}

// GET /api/v1/commands/{number}/orders (FARELO-035, PDV) — lives here
// rather than commands.ts since it returns Order/OrderItem data, which
// this file already owns; the URL just happens to be nested under
// /commands. Client-only (called from app/pdv/page.tsx), same relative-
// path convention as createOrder above.
export async function listCommandOrders(
  commandNumber: number,
): Promise<Order[]> {
  const response = await fetch(`/api/v1/commands/${commandNumber}/orders`, {
    cache: "no-store",
    headers: authHeaders(),
  });
  return parseResponse<Order[]>(response);
}

// GET /api/v1/orders (FARELO-059) — the kitchen queue: every order from
// every command still waiting on the kitchen (CREATED/CONFIRMED/PREPARING,
// so anything before READY), oldest first. Client-only (called from
// app/kds/page.tsx with polling), same relative-path/no-store convention
// as listCommandOrders above.
export async function listKitchenQueue(): Promise<Order[]> {
  const response = await fetch("/api/v1/orders", {
    cache: "no-store",
    headers: authHeaders(),
  });
  return parseResponse<Order[]>(response);
}

// POST /api/v1/orders/{id}/preparing (FARELO-057) — CREATED → PREPARING.
// POST /api/v1/orders/{id}/ready (FARELO-058) — PREPARING → READY.
// Both take no request body; the backend returns the updated OrderResponse
// (same shape as everything else in this file). First callers of either
// endpoint from the frontend — app/kds/page.tsx (FARELO-059/KDS).
export async function markOrderPreparing(orderId: string): Promise<Order> {
  const response = await fetch(`/api/v1/orders/${orderId}/preparing`, {
    method: "POST",
    headers: authHeaders(),
  });
  return parseResponse<Order>(response);
}

export async function markOrderReady(orderId: string): Promise<Order> {
  const response = await fetch(`/api/v1/orders/${orderId}/ready`, {
    method: "POST",
    headers: authHeaders(),
  });
  return parseResponse<Order>(response);
}

// POST /api/v1/orders/{id}/deliver — READY → DELIVERED, closes the normal
// order lifecycle.
// POST /api/v1/orders/{id}/cancel — CANCELLED, from any non-terminal status
// (CREATED/CONFIRMED/PREPARING/READY).
// Same no-body/updated-OrderResponse shape as markOrderPreparing/
// markOrderReady above. First callers of either endpoint from the
// frontend — app/pdv/page.tsx (OrderCard), closing the gap where an order
// stayed visually stuck in READY forever.
export async function markOrderDelivered(orderId: string): Promise<Order> {
  const response = await fetch(`/api/v1/orders/${orderId}/deliver`, {
    method: "POST",
    headers: authHeaders(),
  });
  return parseResponse<Order>(response);
}

export async function markOrderCancelled(orderId: string): Promise<Order> {
  const response = await fetch(`/api/v1/orders/${orderId}/cancel`, {
    method: "POST",
    headers: authHeaders(),
  });
  return parseResponse<Order>(response);
}

// POST /api/v1/orders/{orderId}/items/{itemId}/cancel (FARELO-200/201) —
// cancels a single line item without touching the rest of the order.
// `description` is required by the backend only when `reason` is "OTHER"
// (see OrderItemCancelRequest's javadoc); the caller (app/pdv/page.tsx)
// enforces the same rule in its UI before calling this.
export async function cancelOrderItem(
  orderId: string,
  itemId: string,
  reason: OrderItemCancelReason,
  description?: string,
): Promise<OrderItem> {
  const response = await fetch(
    `/api/v1/orders/${orderId}/items/${itemId}/cancel`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({ reason, description }),
    },
  );
  return parseResponse<OrderItem>(response);
}

// POST /api/v1/orders/{orderId}/items/{itemId}/duplicate (FARELO-318,
// "Duplicar item") — adds a new line for the same product/quantity, with
// its own fresh price snapshot.
export async function duplicateOrderItem(
  orderId: string,
  itemId: string,
): Promise<OrderItem> {
  const response = await fetch(
    `/api/v1/orders/${orderId}/items/${itemId}/duplicate`,
    { method: "POST", headers: authHeaders() },
  );
  return parseResponse<OrderItem>(response);
}

// POST /api/v1/orders/{orderId}/items/{itemId}/resend (FARELO-318,
// "Renotificar item") — queues one more kitchen ticket for a single item,
// without reprinting the rest of the order. Backend returns a PrintJob;
// this UI only needs the confirmation that it was queued, so the response
// isn't typed further here.
export async function resendOrderItemToProduction(
  orderId: string,
  itemId: string,
): Promise<void> {
  const response = await fetch(
    `/api/v1/orders/${orderId}/items/${itemId}/resend`,
    { method: "POST", headers: authHeaders() },
  );
  return parseVoidResponse(response);
}

// POST /api/v1/orders/{orderId}/resend (FARELO-319, "Enviar novamente os
// itens para produção") — reprints every current (non-cancelled) item of
// the order, one kitchen ticket per production station, regardless of
// this order's printOnConfirm flag (a manual resend always works).
export async function resendOrderToProduction(orderId: string): Promise<void> {
  const response = await fetch(`/api/v1/orders/${orderId}/resend`, {
    method: "POST",
    headers: authHeaders(),
  });
  return parseVoidResponse(response);
}

// PATCH /api/v1/orders/{id}/waiter (FARELO-314) — assign/reassign the
// waiter/attendant responsible for the order (userId truthy), or clear the
// assignment (userId null/undefined).
export async function assignOrderWaiter(
  orderId: string,
  userId: string | null,
): Promise<Order> {
  const response = await fetch(`/api/v1/orders/${orderId}/waiter`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ userId }),
  });
  return parseResponse<Order>(response);
}
