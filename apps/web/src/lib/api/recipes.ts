// Thin client for /api/v1/recipes (see docs/api.md, Epic 7 — estoque).
// Client-only — called from app/admin/inventory/page.tsx.

import { authHeaders, parseResponse, parseVoidResponse } from "./client";
import type { IngredientUnit } from "./ingredients";

export type Recipe = {
  id: string;
  productId: string;
  productName: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type RecipeItem = {
  id: string;
  recipeId: string;
  ingredientId: string;
  ingredientName: string;
  ingredientUnit: IngredientUnit;
  quantity: number;
  createdAt: string;
  updatedAt: string;
};

const RECIPES_URL = "/api/v1/recipes";

export async function listRecipes(): Promise<Recipe[]> {
  const response = await fetch(RECIPES_URL, {
    cache: "no-store",
    headers: authHeaders(),
  });
  return parseResponse<Recipe[]>(response);
}

export async function createRecipe(productId: string): Promise<Recipe> {
  const response = await fetch(RECIPES_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ productId }),
  });
  return parseResponse<Recipe>(response);
}

// Não há reativação — uma nova receita substitui a desativada.
export async function deactivateRecipe(id: string): Promise<Recipe> {
  const response = await fetch(`${RECIPES_URL}/${id}/deactivate`, {
    method: "PATCH",
    headers: authHeaders(),
  });
  return parseResponse<Recipe>(response);
}

export async function listRecipeItems(recipeId: string): Promise<RecipeItem[]> {
  const response = await fetch(`${RECIPES_URL}/${recipeId}/items`, {
    cache: "no-store",
    headers: authHeaders(),
  });
  return parseResponse<RecipeItem[]>(response);
}

export async function addRecipeItem(
  recipeId: string,
  ingredientId: string,
  quantity: number,
): Promise<RecipeItem> {
  const response = await fetch(`${RECIPES_URL}/${recipeId}/items`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ ingredientId, quantity }),
  });
  return parseResponse<RecipeItem>(response);
}

export async function removeRecipeItem(
  recipeId: string,
  itemId: string,
): Promise<void> {
  const response = await fetch(`${RECIPES_URL}/${recipeId}/items/${itemId}`, {
    method: "DELETE",
    headers: authHeaders(),
  });
  return parseVoidResponse(response);
}
