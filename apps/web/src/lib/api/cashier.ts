import { authHeaders, parseResponse } from "./client";

export type Shift = {
  id: string;
  name: string;
  openingAccountName: string;
  closingAccountName: string;
  active: boolean;
};

export type CashSession = {
  id: string;
  shiftId: string;
  shiftName: string;
  openedByUserId: string;
  openedByUserName: string;
  openedAt: string;
  status: "OPEN" | "CLOSED";
  closingAmount: number | null;
  closedAt: string | null;
  reconciliationStatus: "PENDING" | "MATCHED" | "MISMATCHED";
  expectedAmount: number | null;
  reconciliationDifference: number | null;
};

export type CashMovement = {
  id: string;
  type: "WITHDRAWAL" | "SUPPLY" | "EXPENSE" | "INCOME";
  amount: number;
  description: string | null;
  createdByUserName: string;
  createdAt: string;
};

export type MovementAction =
  "withdrawals" | "supplies" | "expenses" | "incomes";

async function post<T>(url: string, body?: unknown): Promise<T> {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return parseResponse<T>(response);
}

export async function listShifts(): Promise<Shift[]> {
  const response = await fetch("/api/v1/shifts", {
    cache: "no-store",
    headers: authHeaders(),
  });
  return parseResponse<Shift[]>(response);
}

export async function listCashSessions(): Promise<CashSession[]> {
  const response = await fetch("/api/v1/cash-sessions", {
    cache: "no-store",
    headers: authHeaders(),
  });
  return parseResponse<CashSession[]>(response);
}

export async function listCashMovements(id: string): Promise<CashMovement[]> {
  const response = await fetch(`/api/v1/cash-sessions/${id}/movements`, {
    cache: "no-store",
    headers: authHeaders(),
  });
  return parseResponse<CashMovement[]>(response);
}

export const openCashSession = (shiftId: string) =>
  post<CashSession>("/api/v1/cash-sessions", { shiftId });

export const closeCashSession = (id: string, closingAmount: string) =>
  post<CashSession>(`/api/v1/cash-sessions/${id}/close`, { closingAmount });

export const reconcileCashSession = (id: string) =>
  post<CashSession>(`/api/v1/cash-sessions/${id}/reconcile`);

export const recordCashMovement = (
  id: string,
  action: MovementAction,
  amount: string,
  description: string,
) =>
  post<CashMovement>(`/api/v1/cash-sessions/${id}/${action}`, {
    amount,
    description,
  });
