// Thin client for the /api/v1/commands endpoint (see docs/api.md).
//
// Unlike categories.ts/products.ts (client components calling a relative
// path through the Next.js rewrite in next.config.ts), this is meant to be
// called from a Server Component during SSR — see
// app/c/[commandNumber]/page.tsx. A Server Component's fetch runs in
// Node.js, not the browser: there's no CORS to avoid and no rewrite to go
// through (rewrites only apply to requests arriving *at* the Next.js
// server, not ones it makes itself), so this talks to the backend directly
// via API_BASE_URL — the same env var/default next.config.ts uses for the
// proxy.

import { parseResponse } from "./client";

const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8080";

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
  const response = await fetch(`${API_BASE_URL}/api/v1/commands/${number}`, {
    // Command status changes over time (opened, closed, blocked...) —
    // never serve a stale cached response for this.
    cache: "no-store",
  });
  return parseResponse<Command>(response);
}
