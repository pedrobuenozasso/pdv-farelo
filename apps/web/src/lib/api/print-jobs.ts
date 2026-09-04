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

export type PrintJobContent = {
  commandNumber: number;
  productionStation: "BAR" | "KITCHEN" | null;
  items: { productName: string; quantity: number }[];
};

export type PrintJob = {
  id: string;
  orderId: string;
  content: PrintJobContent;
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
