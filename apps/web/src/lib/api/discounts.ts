// Thin client for the /api/v1/commands/{number}/discounts endpoints
// (FARELO-230/231/232). Client-only — called from app/pdv/page.tsx, same
// relative-path convention as payments.ts.

import { authHeaders, parseResponse } from "./client";

export type DiscountType = "FIXED_AMOUNT" | "PERCENTAGE";

export type Discount = {
  id: string;
  commandNumber: number;
  type: DiscountType;
  // Only populated for PERCENTAGE — mirrors the backend's exclusivity
  // (DiscountRequest's @AssertTrue rules).
  percentage: number | null;
  originalAmount: number;
  discountedAmount: number;
  reason: string | null;
  appliedByUserName: string;
  createdAt: string;
};

export type ApplyDiscountInput =
  | { type: "FIXED_AMOUNT"; amount: number; reason?: string }
  | { type: "PERCENTAGE"; percentage: number; reason?: string };

export async function listDiscounts(commandNumber: number): Promise<Discount[]> {
  const response = await fetch(`/api/v1/commands/${commandNumber}/discounts`, {
    cache: "no-store",
    headers: authHeaders(),
  });
  return parseResponse<Discount[]>(response);
}

export async function applyDiscount(
  commandNumber: number,
  input: ApplyDiscountInput,
): Promise<Discount> {
  const response = await fetch(`/api/v1/commands/${commandNumber}/discounts`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify(input),
  });
  return parseResponse<Discount>(response);
}
