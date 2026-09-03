package com.farelo.api.inventory.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/v1/ingredients/{ingredientId}/movements}
 * (FARELO-094, "Criar entrada manual de estoque"). Never expose the JPA
 * entity directly on the API (see AGENTS.md) — this is the boundary DTO.
 *
 * <p>No {@code ingredientId} field here — it comes from the URL path, not
 * the body (the endpoint is already scoped to one ingredient), same
 * convention as {@link RecipeItemRequest} not repeating {@code recipeId}.
 *
 * <p><b>Deliberately no {@code type} field.</b> This endpoint is the manual
 * *entry* flow specifically — a human recording that stock physically
 * arrived — not a generic "create any {@code InventoryMovementType}"
 * endpoint. Letting the client pick the type would let a client submit
 * {@code ORDER_CONSUMPTION}/{@code LOSS}/etc. through a URL that has nothing
 * to do with orders or losses, bypassing whatever validation those flows
 * will eventually need (FARELO-096/098). The type is hardcoded to
 * {@code PURCHASE} server-side in {@code InventoryMovementService#create}
 * instead.
 *
 * <p>{@code quantity} must be strictly positive (@{@code Positive}, same
 * validation-layer pattern as {@link RecipeItemRequest#quantity()}) — a
 * manual stock entry that is zero or negative isn't what {@code PURCHASE}
 * means (see {@link com.farelo.api.inventory.InventoryMovement}'s javadoc:
 * sign encodes direction, and {@code PURCHASE} is always stock coming in).
 * Always in the referenced {@link com.farelo.api.inventory.Ingredient}'s
 * base unit ({@link com.farelo.api.inventory.Ingredient#getUnit()}), same
 * "no purchase-unit conversion" convention already established by {@link
 * RecipeItemRequest#quantity()}.
 */
public record InventoryMovementRequest(@NotNull @Positive BigDecimal quantity) {
}
