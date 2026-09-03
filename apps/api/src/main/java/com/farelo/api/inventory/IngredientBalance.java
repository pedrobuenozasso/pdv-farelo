package com.farelo.api.inventory;

import java.math.BigDecimal;

/**
 * FARELO-095 ("Calcular saldo do ingrediente"): an {@link Ingredient}'s
 * current stock balance, bundled with enough of the ingredient itself (its
 * {@link Ingredient#getUnit()}) that a caller can interpret the number
 * without a second lookup — {@code 500} means nothing on its own, {@code 500
 * GRAM} does.
 *
 * <p>Not a JPA entity and never persisted anywhere: {@code balance} is always
 * computed on demand from the ledger (see {@link
 * InventoryMovementRepository#sumQuantityByIngredientId}), never stored as a
 * field (prompt mestre seção 13: "Não armazenar apenas um número editável de
 * saldo... O saldo deve ser rastreável (derivado do ledger, nunca editado
 * diretamente)"). This is a plain in-memory carrier returned by {@link
 * InventoryMovementService#getBalance(java.util.UUID)}, analogous to how
 * {@code InventoryMovementResponse}/{@code IngredientResponse} shape API
 * output — except this one lives in the domain package (not {@code .web})
 * because it's consumed by the service layer's return type, not just a
 * response DTO.
 *
 * @param ingredient the ingredient the balance was computed for
 * @param balance the sum of every {@link InventoryMovement#getQuantity()}
 *     row recorded for {@code ingredient}, expressed in {@code
 *     ingredient.getUnit()}'s base unit; {@code 0} (never {@code null}) when
 *     the ingredient has no movements yet, per {@code
 *     sumQuantityByIngredientId}'s {@code COALESCE(SUM(...), 0)}
 */
public record IngredientBalance(Ingredient ingredient, BigDecimal balance) {
}
