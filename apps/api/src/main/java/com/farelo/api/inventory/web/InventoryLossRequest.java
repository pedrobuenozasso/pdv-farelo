package com.farelo.api.inventory.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Request body for {@code POST
 * /api/v1/ingredients/{ingredientId}/losses} (FARELO-098, "Criar movimento
 * de perda"). Never expose the JPA entity directly on the API (see
 * AGENTS.md) — this is the boundary DTO, same role {@link
 * InventoryMovementRequest} plays for {@code POST .../movements}.
 *
 * <p>No {@code ingredientId} field — comes from the URL path, not the body
 * (the endpoint is already scoped to one ingredient), same convention as
 * {@link InventoryMovementRequest} not repeating it either.
 *
 * <p><b>{@code quantity} is always a POSITIVE magnitude</b> — "how much was
 * lost", not a signed delta. {@code @Positive}, same validation-layer
 * pattern as {@link InventoryMovementRequest#quantity()}: a human reporting
 * a loss thinks in terms of "we lost 500g of X", never "-500g of X" — the
 * negative sign that actually lands in the ledger (see {@link
 * com.farelo.api.inventory.InventoryMovement}'s javadoc: sign encodes
 * direction, and {@code LOSS} is always stock going out) is applied
 * server-side by {@link
 * com.farelo.api.inventory.InventoryMovementService#recordLoss}
 * when constructing the row, not something the client encodes itself —
 * same "sign is a server-side detail, not a client-facing encoding"
 * reasoning already established for {@code PURCHASE} by {@link
 * InventoryMovementRequest}'s javadoc and {@code
 * InventoryMovementService#create}. Always in the referenced {@link
 * com.farelo.api.inventory.Ingredient}'s base unit ({@link
 * com.farelo.api.inventory.Ingredient#getUnit()}), same "no purchase-unit
 * conversion" convention as {@link InventoryMovementRequest#quantity()}.
 *
 * <p><b>No {@code reason}/{@code note} field</b> — considered and
 * deliberately left out of scope for this ticket. Neither the prompt
 * mestre (seção 13, which only names {@code LOSS} as one of the seven
 * {@code InventoryMovementType} values, with no mention of a reason/note
 * field on the movement itself) nor {@link
 * com.farelo.api.inventory.InventoryMovementType}'s own javadoc for {@code
 * LOSS} ("stock removed for spoilage/breakage/theft, not a sale" — a
 * description of the *type*, not a request for a free-text field on the
 * entity) asks for one. Adding a column to the append-only {@link
 * com.farelo.api.inventory.InventoryMovement} ledger for a single new
 * producer, with no concrete consumer reading it back, would be exactly the
 * kind of speculative addition this codebase's ticket precedent avoids
 * (same YAGNI reasoning already applied to {@code Ingredient} not carrying
 * stock-threshold fields until FARELO-099 needs them — see {@code
 * docs/domain-model.md}, seção `inventory`/`Ingredient`). If a future
 * ticket needs a reason/audit trail for a loss, that is that ticket's
 * column to add — deferring it here costs nothing today (no migration, no
 * unused field on every other {@code InventoryMovement} row of every other
 * type).
 */
public record InventoryLossRequest(@NotNull @Positive BigDecimal quantity) {
}
