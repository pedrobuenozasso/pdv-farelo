// Thin client for /api/v1/print-jobs (see docs/api.md, Epic 6).
// Client-only — called from app/admin/print-jobs/page.tsx.
//
// GET only ever returns PENDING jobs (the backend has no listing for
// FAILED ones — see PrintJobRepository, which only backs a "findByStatus
// PENDING" query) — there is currently no way for any client, including
// this one, to discover a FAILED job's id, so a "retry failed jobs" UI
// isn't buildable against today's API despite POST .../retry existing.
// This page is deliberately read-only + informational until a
// GET .../print-jobs?status=FAILED (or similar) endpoint exists.

import { authHeaders, parseResponse } from "./client";

export type PrintJobStatus = "PENDING" | "PRINTED" | "FAILED";

// FARELO-210/211: a job is now one of two kinds — KITCHEN_TICKET (the
// original, order-scoped) or COMMAND_CHECK ("conferência", command-scoped).
export type PrintJobType = "KITCHEN_TICKET" | "COMMAND_CHECK";

export type PrintJobContent = {
  commandNumber: number;
  productionStation: "BAR" | "KITCHEN" | null;
  items: { productName: string; quantity: number }[];
};

export type CommandCheckContent = {
  commandNumber: number;
  items: {
    productName: string;
    quantity: number;
    unitPrice: number;
    lineTotal: number;
  }[];
  total: number;
};

export type PrintJob = {
  id: string;
  type: PrintJobType;
  // Exactly one of each pair is non-null, depending on type — mirrors
  // PrintJobResponse on the backend (see its javadoc).
  orderId: string | null;
  commandNumber: number | null;
  content: PrintJobContent | null;
  commandCheckContent: CommandCheckContent | null;
  status: PrintJobStatus;
  retryCount: number;
  createdAt: string;
};

export async function listPendingPrintJobs(): Promise<PrintJob[]> {
  const response = await fetch("/api/v1/print-jobs", {
    cache: "no-store",
    headers: authHeaders(),
  });
  return parseResponse<PrintJob[]>(response);
}

// POST /api/v1/commands/{number}/print-conference (FARELO-211/212) —
// queues a COMMAND_CHECK job for the comanda; the actual printing happens
// asynchronously via the Edge Agent, same as every other PrintJob.
export async function printConference(commandNumber: number): Promise<PrintJob> {
  const response = await fetch(
    `/api/v1/commands/${commandNumber}/print-conference`,
    { method: "POST", headers: authHeaders() },
  );
  return parseResponse<PrintJob>(response);
}
