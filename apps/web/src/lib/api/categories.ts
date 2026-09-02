// Thin client for the /api/v1/categories endpoints (see docs/api.md).

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

const CATEGORIES_URL = "/api/v1/categories";

export async function listCategories(): Promise<Category[]> {
  const response = await fetch(CATEGORIES_URL);
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
