// Thin client for the /api/v1/products endpoints (see docs/api.md).
//
// Isomorphic (FARELO-042/043) — same reasoning as categories.ts: works
// from the Admin's client components (relative path, via the
// next.config.ts rewrite) and from the customer menu page's Server
// Component (absolute backend URL during SSR).

import { authHeaders, parseResponse } from "./client";

export type ProductionStation = "BAR" | "KITCHEN";

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
  productionStation: ProductionStation | null;
  createdAt: string;
  updatedAt: string;
};

// FARELO-271: availableOnMenu/availableOnPos/productionStation are all
// already accepted by the backend on create (ProductRequest) — active
// deliberately isn't (a product always starts active, same reasoning
// ProductUpdateRequest's javadoc documents for why active only appears on
// the update shape).
export type CreateProductInput = {
  name: string;
  description?: string;
  price: number;
  categoryId: string;
  imageUrl?: string;
  availableOnMenu?: boolean;
  availableOnPos?: boolean;
  productionStation?: ProductionStation;
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
  productionStation?: ProductionStation;
};

const API_BASE_URL =
  typeof window === "undefined"
    ? (process.env.API_BASE_URL ?? "http://localhost:8080")
    : "";

const PRODUCTS_URL = `${API_BASE_URL}/api/v1/products`;

export async function listProducts(): Promise<Product[]> {
  // Menu-visible data (active/availableOnMenu) changes via the Admin at
  // any time — never serve a stale cached response for this (matters most
  // for the SSR call).
  const response = await fetch(PRODUCTS_URL, { cache: "no-store" });
  return parseResponse<Product[]>(response);
}

export async function createProduct(
  input: CreateProductInput,
): Promise<Product> {
  const response = await fetch(PRODUCTS_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
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
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify(input),
  });
  return parseResponse<Product>(response);
}
