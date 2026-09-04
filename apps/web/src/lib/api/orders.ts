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

import { authHeaders, parseResponse } from "./client";

export type OrderStatus =
  "CREATED" | "CONFIRMED" | "PREPARING" | "READY" | "DELIVERED" | "CANCELLED";

export type OrderItem = {
  id: string;
  productId: string;
  productName: string;
  quantity: number;
  unitPrice: number;
};

export type Order = {
  id: string;
  commandNumber: number;
  status: OrderStatus;
  items: OrderItem[];
  customerName: string | null;
  customerPhone: string | null;
  createdAt: string;
};

export type CreateOrderInput = {
  commandNumber: number;
  items: { productId: string; quantity: number }[];
  customerName?: string;
  customerPhone?: string;
};

export async function createOrder(input: CreateOrderInput): Promise<Order> {
  const response = await fetch("/api/v1/orders", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
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
