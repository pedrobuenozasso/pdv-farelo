package com.farelo.api.inventory;

/**
 * What kind of event an {@link InventoryMovement} row represents.
 *
 * <p><b>FARELO-093 scope</b>: this is the literal, complete list from the
 * prompt mestre (seção 13: "Tipos: {@code PURCHASE}, {@code
 * ORDER_CONSUMPTION}, {@code LOSS}, {@code ADJUSTMENT}, {@code RETURN},
 * {@code CANCELLATION}, {@code INTERNAL_CONSUMPTION}. O saldo deve ser
 * rastreável (derivado do ledger, nunca editado diretamente)"). Unlike
 * {@code IngredientUnit} (FARELO-090), which deliberately trimmed the
 * prompt mestre's unit list down to only what a real consumer needed at the
 * time, this enum is used verbatim: the master spec already names all seven
 * values explicitly and exhaustively for this exact entity, so there's
 * nothing to "guess" a design for — trimming it here would just guarantee a
 * {@code CHECK} constraint migration the moment each of the roadmap tickets
 * below lands, for a value the spec already told us to expect:
 *
 * <ul>
 *   <li>{@code ORDER_CONSUMPTION} — FARELO-096 ("Consumir receita ao criar
 *       pedido"), the literal example in prompt mestre seção 16 ({@code
 *       ORDER_CONSUMPTION orderId=123 ingredientId=5}). See {@link
 *       InventoryMovement#getOrderId()} for how this type is expected to
 *       carry its order origin.</li>
 *   <li>{@code PURCHASE} — FARELO-094 ("Criar entrada manual de estoque"):
 *       stock added by a manual purchase entry.</li>
 *   <li>{@code LOSS} — FARELO-098 ("Criar movimento de perda"): stock
 *       removed for spoilage/breakage/theft, not a sale.</li>
 *   <li>{@code ADJUSTMENT}, {@code RETURN}, {@code CANCELLATION}, {@code
 *       INTERNAL_CONSUMPTION} — named by the prompt mestre alongside the
 *       above but not yet tied to a specific numbered roadmap ticket
 *       (e.g. {@code CANCELLATION}/{@code RETURN} plausibly reverse an
 *       {@code ORDER_CONSUMPTION} when an order/item is cancelled or
 *       returned; {@code ADJUSTMENT} plausibly covers a manual correction
 *       distinct from a {@code PURCHASE}; {@code INTERNAL_CONSUMPTION}
 *       plausibly covers stock used outside a sale, e.g. staff meals or
 *       waste testing). No code produces any of these four yet — they exist
 *       here only because the master spec already committed to their names,
 *       so the {@code CHECK} constraint (see
 *       {@code V21__create_inventory_movement_table.sql}) matches the full
 *       set from day one instead of needing widening later.</li>
 * </ul>
 *
 * <p>This ticket (FARELO-093) creates no producer for <em>any</em> of these
 * values — nothing in this codebase yet constructs an {@link
 * InventoryMovement}. That remains true until FARELO-094/096/098 (and
 * whichever future tickets cover {@code ADJUSTMENT}/{@code RETURN}/{@code
 * CANCELLATION}/{@code INTERNAL_CONSUMPTION}) land.
 */
public enum InventoryMovementType {
    PURCHASE,
    ORDER_CONSUMPTION,
    LOSS,
    ADJUSTMENT,
    RETURN,
    CANCELLATION,
    INTERNAL_CONSUMPTION
}
