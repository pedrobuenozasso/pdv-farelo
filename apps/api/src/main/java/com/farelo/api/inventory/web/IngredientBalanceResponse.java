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
 */
public record IngredientBalanceResponse(UUID ingredientId, BigDecimal balance, IngredientUnit unit) {

    public static IngredientBalanceResponse from(IngredientBalance ingredientBalance) {
        return new IngredientBalanceResponse(
                ingredientBalance.ingredient().getId(),
                ingredientBalance.balance(),
                ingredientBalance.ingredient().getUnit());
    }

}
