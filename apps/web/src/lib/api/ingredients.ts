// Thin client for /api/v1/ingredients (see docs/api.md, Epic 7 — estoque).
// Client-only — called from app/admin/inventory/page.tsx.

import { authHeaders, parseResponse } from "./client";

export type IngredientUnit = "GRAM" | "MILLILITER" | "UNIT";

export type Ingredient = {
  id: string;
  name: string;
  unit: IngredientUnit;
  active: boolean;
  minimumStock: number | null;
  createdAt: string;
  updatedAt: string;
};

export type CreateIngredientInput = {
  name: string;
  unit: IngredientUnit;
  minimumStock?: number;
};

// PUT is a full replace — omitting minimumStock CLEARS a previously-set
// threshold (see docs/api.md), not "leave unchanged".
export type UpdateIngredientInput = {
  name: string;
  unit: IngredientUnit;
  active: boolean;
  minimumStock?: number;
};

export type InventoryMovementType =
  | "PURCHASE"
  | "ORDER_CONSUMPTION"
  | "LOSS"
  | "ADJUSTMENT"
  | "RETURN"
  | "CANCELLATION"
  | "INTERNAL_CONSUMPTION";

export type InventoryMovement = {
  id: string;
  ingredientId: string;
  quantity: number;
  type: InventoryMovementType;
  orderId: string | null;
  createdAt: string;
};

export type IngredientBalance = {
  ingredientId: string;
  balance: number;
  unit: IngredientUnit;
  belowMinimum: boolean;
};

const INGREDIENTS_URL = "/api/v1/ingredients";

export async function listIngredients(): Promise<Ingredient[]> {
  const response = await fetch(INGREDIENTS_URL, {
    cache: "no-store",
    headers: authHeaders(),
  });
  return parseResponse<Ingredient[]>(response);
}

export async function createIngredient(
  input: CreateIngredientInput,
): Promise<Ingredient> {
  const response = await fetch(INGREDIENTS_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify(input),
  });
  return parseResponse<Ingredient>(response);
}

export async function updateIngredient(
  id: string,
  input: UpdateIngredientInput,
): Promise<Ingredient> {
  const response = await fetch(`${INGREDIENTS_URL}/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify(input),
  });
  return parseResponse<Ingredient>(response);
}

export async function getIngredientBalance(
  ingredientId: string,
): Promise<IngredientBalance> {
  const response = await fetch(`${INGREDIENTS_URL}/${ingredientId}/balance`, {
    cache: "no-store",
    headers: authHeaders(),
  });
  return parseResponse<IngredientBalance>(response);
}

export async function listIngredientMovements(
  ingredientId: string,
): Promise<InventoryMovement[]> {
  const response = await fetch(`${INGREDIENTS_URL}/${ingredientId}/movements`, {
    cache: "no-store",
    headers: authHeaders(),
  });
  return parseResponse<InventoryMovement[]>(response);
}

// Requer ADMIN/MANAGER — registra uma compra (entrada) manual de estoque.
export async function recordPurchase(
  ingredientId: string,
  quantity: number,
): Promise<InventoryMovement> {
  const response = await fetch(`${INGREDIENTS_URL}/${ingredientId}/movements`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ quantity }),
  });
  return parseResponse<InventoryMovement>(response);
}

// Requer ADMIN/MANAGER — `quantity` é a magnitude positiva perdida; o
// backend grava o movimento como negativo internamente.
export async function recordLoss(
  ingredientId: string,
  quantity: number,
): Promise<InventoryMovement> {
  const response = await fetch(`${INGREDIENTS_URL}/${ingredientId}/losses`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ quantity }),
  });
  return parseResponse<InventoryMovement>(response);
}
