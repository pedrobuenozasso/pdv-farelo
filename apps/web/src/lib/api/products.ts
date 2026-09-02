// Thin client for the /api/v1/products endpoints (see docs/api.md).

import { parseResponse } from "./client";

export type Product = {
  id: string;
  name: string;
  description: string | null;
  price: number;
  active: boolean;
  availableOnMenu: boolean;
  availableOnPos: boolean;
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

// PUT is a full replace: unlike create, active/availableOnMenu/availableOnPos
// are all required by the backend (see ProductUpdateRequest / docs/api.md).
export type UpdateProductInput = {
  name: string;
  description?: string;
  price: number;
  categoryId: string;
  imageUrl?: string;
  active: boolean;
  availableOnMenu: boolean;
  availableOnPos: boolean;
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

export async function updateProduct(
  id: string,
  input: UpdateProductInput,
): Promise<Product> {
  const response = await fetch(`${PRODUCTS_URL}/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return parseResponse<Product>(response);
}
