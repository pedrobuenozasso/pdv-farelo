// Thin client for the /api/v1/commands/* endpoints (see docs/api.md).
//
// Isomorphic (FARELO-035): getCommand() started out called only from a
// Server Component during SSR (app/c/[commandNumber]/page.tsx — no
// browser there, no CORS to avoid, and next.config.ts's rewrite doesn't
// apply since it only affects requests arriving *at* the Next.js server,
// not ones it makes itself). FARELO-035 added open()/close(), called from
// a client component (app/pdv/page.tsx) instead — so this module now
// follows the same isomorphic pattern as categories.ts/products.ts:
// relative path through the rewrite in the browser, absolute URL via
// API_BASE_URL during SSR, decided at runtime by `typeof window`.

import { parseResponse } from "./client";

const API_BASE_URL =
  typeof window === "undefined"
    ? (process.env.API_BASE_URL ?? "http://localhost:8080")
    : "";

const COMMANDS_URL = `${API_BASE_URL}/api/v1/commands`;

export type CommandStatus =
  "AVAILABLE" | "OPEN" | "PAYMENT_REQUESTED" | "CLOSED" | "BLOCKED";

export type Command = {
  id: string;
  number: number;
  status: CommandStatus;
  createdAt: string;
  updatedAt: string;
};

export async function getCommand(number: number): Promise<Command> {
  const response = await fetch(`${COMMANDS_URL}/${number}`, {
    // Command status changes over time (opened, closed, blocked...) —
    // never serve a stale cached response for this.
    cache: "no-store",
  });
  return parseResponse<Command>(response);
}

export async function openCommand(number: number): Promise<Command> {
  const response = await fetch(`${COMMANDS_URL}/${number}/open`, {
    method: "POST",
  });
  return parseResponse<Command>(response);
}

export async function closeCommand(number: number): Promise<Command> {
  const response = await fetch(`${COMMANDS_URL}/${number}/close`, {
    method: "POST",
  });
  return parseResponse<Command>(response);
}
