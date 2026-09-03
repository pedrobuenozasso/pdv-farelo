package com.farelo.api.inventory.web;

import com.farelo.api.inventory.IngredientBalance;
import com.farelo.api.inventory.IngredientUnit;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response body for {@code GET /api/v1/ingredients/{ingredientId}/balance}
 * (FARELO-095). Wraps {@link IngredientBalance} — the JPA entity it carries
 * ({@code Ingredient}) is never returned by the API (see AGENTS.md).
 *
 * <p>{@code unit} is included alongside {@code balance} on purpose, per this
 * ticket's own requirement: a bare number is ambiguous ({@code 500} of
 * what?), so the response carries {@link IngredientUnit} directly instead of
 * making a client issue a second {@code GET /api/v1/ingredients/{id}} just
 * to interpret it.
 *
 * <p>{@code belowMinimum} (FARELO-099, "Criar estoque mínimo") is the
 * natural place to expose "is this ingredient currently low" — this endpoint
 * already computes a live balance, and the threshold it's compared against
 * ({@code Ingredient.minimumStock}) belongs to the same ingredient this
 * response is already about, so no second lookup/endpoint is needed. Simply
 * forwards {@link IngredientBalance#isBelowMinimum()} — see that method's
 * javadoc for the full comparison semantics (strict {@code <}, {@code false}
 * when no threshold is configured, regardless of balance sign).
 */
public record IngredientBalanceResponse(UUID ingredientId, BigDecimal balance, IngredientUnit unit, boolean belowMinimum) {

    public static IngredientBalanceResponse from(IngredientBalance ingredientBalance) {
        return new IngredientBalanceResponse(
                ingredientBalance.ingredient().getId(),
                ingredientBalance.balance(),
                ingredientBalance.ingredient().getUnit(),
                ingredientBalance.isBelowMinimum());
    }

}
