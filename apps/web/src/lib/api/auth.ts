// Thin client for POST /api/v1/auth/login (see docs/api.md, FARELO-121).
//
// Client-only (the login page is a client component, same isomorphic
// non-issue as orders.ts's createOrder) — relative path through the
// Next.js rewrite in next.config.ts.

import { parseResponse } from "./client";

export type LoginInput = {
  email: string;
  password: string;
};

export type LoginResult = {
  token: string;
  expiresAt: string;
};

export async function login(input: LoginInput): Promise<LoginResult> {
  const response = await fetch("/api/v1/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return parseResponse<LoginResult>(response);
}
