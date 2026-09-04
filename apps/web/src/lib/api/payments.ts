// Thin client for the /api/v1/commands/{number}/payments endpoints (see
// docs/api.md, FARELO-140/141/142). Client-only — called from
// app/pdv/page.tsx (the comanda detail panel's payment section), same
// relative-path convention as orders.ts.

import { authHeaders, parseResponse } from "./client";

export type PaymentMethod =
  "PIX" | "CREDIT_CARD" | "DEBIT_CARD" | "CASH" | "OTHER";

export type Payment = {
  id: string;
  commandNumber: number;
  amount: number;
  method: PaymentMethod;
  createdAt: string;
};

export type TotalPaid = {
  commandNumber: number;
  totalPaid: number;
};

// FARELO-223: totalOwed/totalPaid/remaining computed entirely server-side —
// backs GET .../payments/balance. Replaces the client-side
// sum-orders-then-subtract-paid logic apps/pdv/page.tsx used to do itself.
// totalDiscount added by FARELO-230/231/232 — see lib/api/discounts.ts.
export type PaymentBalance = {
  commandNumber: number;
  totalOwed: number;
  totalDiscount: number;
  totalPaid: number;
  remaining: number;
};

export type RecordPaymentInput = {
  amount: number;
  method: PaymentMethod;
  // FARELO-225 ("Tratar troco em dinheiro") — only meaningful/allowed when
  // method is CASH, and must be >= amount (backend validates both). The
  // cash the customer physically handed over; amount stays "valor
  // efetivamente aplicado" exactly as before this ticket.
  amountReceived?: number;
};

// FARELO-225: response shape for recordPayment — changeGiven is computed
// by the backend, never persisted (it's cash handed back, not money the
// business received — see PaymentRequest's javadoc on the backend).
export type RecordPaymentResult = Payment & { changeGiven: number };

export async function getTotalPaid(commandNumber: number): Promise<TotalPaid> {
  const response = await fetch(
    `/api/v1/commands/${commandNumber}/payments/total`,
    { cache: "no-store", headers: authHeaders() },
  );
  return parseResponse<TotalPaid>(response);
}

export async function getPaymentBalance(
  commandNumber: number,
): Promise<PaymentBalance> {
  const response = await fetch(
    `/api/v1/commands/${commandNumber}/payments/balance`,
    { cache: "no-store", headers: authHeaders() },
  );
  return parseResponse<PaymentBalance>(response);
}

export async function recordPayment(
  commandNumber: number,
  input: RecordPaymentInput,
): Promise<RecordPaymentResult> {
  const response = await fetch(`/api/v1/commands/${commandNumber}/payments`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify(input),
  });
  return parseResponse<RecordPaymentResult>(response);
}
