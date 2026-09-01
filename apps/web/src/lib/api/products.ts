// Thin client for the /api/v1/products endpoints (see docs/api.md).
//
// NOTE (FARELO-019): there is no updateProduct/PUT here on purpose — the
// backend has no PUT /api/v1/products/{id} yet. docs/api.md is explicit:
// "Ainda não há PUT/DELETE para produto (escopo de FARELO-016 em diante)",
// and ProductController/ProductService only implement create + listAll.
// Edição de produto fica para quando esse endpoint existir no backend.

import { parseResponse } from "./client";

export type Product = {
  id: string;
  name: string;
  description: string | null;
  price: number;
  active: boolean;
  categoryId: string;
  imageUrl: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CreateProductInput = {
  name: string;
  description?: string;
  price: number;
  categoryId: string;
  imageUrl?: string;
};

const PRODUCTS_URL = "/api/v1/products";

export async function listProducts(): Promise<Product[]> {
  const response = await fetch(PRODUCTS_URL);
  return parseResponse<Product[]>(response);
}

export async function createProduct(
  input: CreateProductInput,
): Promise<Product> {
  const response = await fetch(PRODUCTS_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return parseResponse<Product>(response);
}
