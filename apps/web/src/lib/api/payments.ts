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

export type RecordPaymentInput = {
  amount: number;
  method: PaymentMethod;
};

export async function getTotalPaid(commandNumber: number): Promise<TotalPaid> {
  const response = await fetch(
    `/api/v1/commands/${commandNumber}/payments/total`,
    { cache: "no-store", headers: authHeaders() },
  );
  return parseResponse<TotalPaid>(response);
}

export async function recordPayment(
  commandNumber: number,
  input: RecordPaymentInput,
): Promise<Payment> {
  const response = await fetch(`/api/v1/commands/${commandNumber}/payments`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify(input),
  });
  return parseResponse<Payment>(response);
}
