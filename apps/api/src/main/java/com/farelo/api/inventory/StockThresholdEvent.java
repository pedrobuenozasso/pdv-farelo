package com.farelo.api.inventory;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for the {@code STOCK_LOW}/{@code OUT_OF_STOCK} outbox events
 * (FARELO-100/101, prompt mestre seção 17/29), published by {@link
 * InventoryMovementService} in the same transaction as the stock-reducing
 * movement ({@code recordLoss}/{@code consumeForOrder}) that caused the
 * crossing. Lives in {@code inventory}, not {@code com.farelo.api.outbox} —
 * same "event payload shapes belong to the domain that produces them"
 * reasoning as {@code OrderCreatedEvent}/{@code OrderReadyEvent} (see the
 * outbox package's package-info, "dependency direction").
 *
 * <p>Unlike {@code OrderReadyEvent} (which is deliberately minimal because
 * its one real consumer re-fetches the order from the database), this
 * payload carries enough to be immediately useful without a second lookup —
 * there is no consumer yet (FARELO-113, a future ticket, is the first one),
 * so the shape is chosen for what a human inspecting {@code
 * outbox_event.payload} directly, or a future notification consumer, would
 * plausibly need: which ingredient, how far its balance actually is from the
 * configured threshold, and what that threshold is.
 *
 * @param ingredientId    the ingredient this event is about
 * @param ingredientName  the ingredient's name at the time of publishing (a
 *                        snapshot, not a live reference — same "don't make a
 *                        future reader join back to {@code ingredient} just
 *                        to read a name" reasoning as {@link
 *                        com.farelo.api.ordering.OrderReadyEvent} carrying
 *                        {@code commandNumber} instead of only an id)
 * @param unit            {@code ingredientName}'s unit, so {@code balance}/
 *                         {@code minimumStock} below aren't ambiguous numbers
 * @param balance         the ledger-derived balance that triggered this
 *                        event (see {@link InventoryMovementService#getBalance})
 * @param minimumStock    the ingredient's configured threshold at the time of
 *                        publishing, or {@code null} if none was configured —
 *                        always {@code null} for an {@code OUT_OF_STOCK}
 *                        event published for an ingredient with no threshold
 *                        (see {@link IngredientBalance#isOutOfStock()}'s
 *                        javadoc: that check is threshold-independent)
 */
public record StockThresholdEvent(
        UUID ingredientId,
        String ingredientName,
        IngredientUnit unit,
        BigDecimal balance,
        BigDecimal minimumStock) {

    public static StockThresholdEvent from(Ingredient ingredient, BigDecimal balance) {
        return new StockThresholdEvent(
                ingredient.getId(), ingredient.getName(), ingredient.getUnit(), balance, ingredient.getMinimumStock());
    }

}
