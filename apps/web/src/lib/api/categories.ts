// Thin client for the /api/v1/categories endpoints (see docs/api.md).
//
// Isomorphic (FARELO-042/043): the Admin pages ("use client") call this
// with a relative path, going through the rewrite proxy in
// next.config.ts — there's no `window` there, so no CORS to avoid. The
// customer menu page (app/c/[commandNumber]/page.tsx, a Server Component)
// also calls listCategories() directly during SSR, where there's no
// browser and the rewrite doesn't apply (see commands.ts's comment on
// why), so it needs the backend's absolute URL instead. `typeof window`
// tells them apart at runtime — same module, same functions, either
// context.

import { parseResponse } from "./client";

export { ApiError } from "./client";

export type Category = {
  id: string;
  name: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type CreateCategoryInput = {
  name: string;
};

const API_BASE_URL =
  typeof window === "undefined"
    ? (process.env.API_BASE_URL ?? "http://localhost:8080")
    : "";

const CATEGORIES_URL = `${API_BASE_URL}/api/v1/categories`;

export async function listCategories(): Promise<Category[]> {
  // Menu-visible data changes via the Admin at any time — never serve a
  // stale cached response for this (matters most for the SSR call).
  const response = await fetch(CATEGORIES_URL, { cache: "no-store" });
  return parseResponse<Category[]>(response);
}

export async function createCategory(
  input: CreateCategoryInput,
): Promise<Category> {
  const response = await fetch(CATEGORIES_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return parseResponse<Category>(response);
}
