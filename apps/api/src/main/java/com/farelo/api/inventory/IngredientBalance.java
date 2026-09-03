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

    /**
     * FARELO-099 ("Criar estoque mínimo"): whether {@link #balance} is
     * currently below {@code ingredient}'s configured {@link
     * Ingredient#getMinimumStock()} threshold.
     *
     * <p>{@code false} when no threshold is configured ({@code
     * getMinimumStock() == null}) — per that field's own javadoc, {@code
     * null} means "nobody has decided a threshold for this ingredient yet",
     * so there is nothing to compare {@code balance} against and this
     * ingredient can never be reported as low, no matter how negative {@code
     * balance} is. Negative balances are reachable today: {@code
     * InventoryMovementService#consumeForOrder}'s "no stock-sufficiency
     * check" design (FARELO-096/097) deliberately allows a balance to go
     * negative, and this method still returns {@code false} for such an
     * ingredient when it has no configured threshold — exactly the "never
     * flagged low regardless of balance" contract this ticket requires.
     *
     * <p>Otherwise, strictly less than the threshold ({@code balance <
     * minimumStock}) — a balance exactly <em>at</em> the threshold is not
     * "below" it (that reads as "at the minimum", the boundary where an
     * operator should reorder soon, not yet a violation), same as how a
     * strict {@code <} comparison is the natural reading of "below" in
     * everyday language. This method only computes and exposes the boolean;
     * it deliberately does not publish any event ({@code STOCK_LOW}/{@code
     * OUT_OF_STOCK} are FARELO-100/101, explicitly out of scope for
     * FARELO-099 — see this ticket's own notes).
     */
    public boolean isBelowMinimum() {
        BigDecimal minimumStock = ingredient.getMinimumStock();
        return minimumStock != null && balance.compareTo(minimumStock) < 0;
    }

}
