// Thin client for POST /api/v1/orders (see docs/api.md).
//
// Only called from the cart's client component (menu.tsx, FARELO-045) —
// no Server Component needs it, so this uses a plain relative path
// through the Next.js rewrite in next.config.ts, unlike the isomorphic
// categories.ts/products.ts. Adapt the same way those two did if a
// server-side caller shows up later.
//
// IMPORTANT: the request body only carries `commandNumber` and `items`.
// The checkout form also collects the customer's name/phone (FARELO-045,
// prompt mestre seção 6), but those fields are NOT sent here — the
// backend's CreateOrderRequest has no customer fields at all (no
// `customer` domain exists yet, see docs/api.md's note on
// POST /api/v1/orders). Don't add them to CreateOrderInput without first
// checking whether the backend has grown a place to put them.

import { parseResponse } from "./client";

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
  createdAt: string;
};

export type CreateOrderInput = {
  commandNumber: number;
  items: { productId: string; quantity: number }[];
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
  });
  return parseResponse<Order[]>(response);
}
