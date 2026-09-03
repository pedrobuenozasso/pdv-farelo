package com.farelo.api.inventory;

import java.math.BigDecimal;

/**
 * FARELO-127 ("Auditar ajuste de estoque"): the JSON shape {@link
 * InventoryMovementService#create}/{@link InventoryMovementService#recordLoss}
 * feed to {@code AuditLogService#record}'s {@code newValue} parameter —
 * serializes (via the injected {@link com.fasterxml.jackson.databind.ObjectMapper},
 * same "record + {@code writeValueAsString}" pattern {@code ProductService}
 * already established for {@code ProductPriceSnapshot}, FARELO-126) to
 * exactly {@code {"type": "PURCHASE", "quantity": 3000}} — the {@link
 * InventoryMovementType} and the signed {@code quantity} exactly as written
 * to the {@link InventoryMovement} row this call produced (positive for
 * {@code create()}/{@code PURCHASE}, negative for {@code recordLoss()}/
 * {@code LOSS} — same sign convention documented on {@link
 * InventoryMovement#getQuantity()}, not renormalized to a magnitude here).
 *
 * <h2>Why there is no {@code previousValue} for either producer</h2>
 *
 * {@code ProductPriceSnapshot} (FARELO-126) exists because a price change is
 * a mutation of one field on one row — {@code Product.price} genuinely has a
 * "before" and an "after", the same column at two points in time. A stock
 * movement is not that shape at all: {@link InventoryMovement} is itself an
 * append-only ledger row (see its own javadoc, "Append-only, never
 * mutated") — nothing on {@link Ingredient} is overwritten when {@code
 * create()}/{@code recordLoss()} runs; a brand-new fact is appended to a
 * ledger that never had a mutable "current value" to snapshot the prior
 * state of in the first place. There is therefore nothing analogous to
 * "the old price" to capture — both producers call {@code
 * AuditLogService#record} with {@code previousValue = null}, the same
 * "a producer that only cares about what it became has a legitimate reason
 * to leave the other null" case {@link com.farelo.api.audit.AuditLog}'s own
 * javadoc ("Design decision 4") already anticipates.
 *
 * <h2>Why {@code resultingBalance} is deliberately left out</h2>
 *
 * An ingredient's balance <em>after</em> this movement (via {@link
 * InventoryMovementRepository#sumQuantityByIngredientId}, the same query
 * {@link InventoryMovementService#getBalance} and {@code
 * publishStockThresholdEventIfNeeded} already use) was considered and
 * deliberately left out of this snapshot. Including it would mean an extra
 * {@code SUM(quantity)} query on every single audited call — for {@code
 * create()} specifically, a query that method has no other reason to run
 * today (unlike {@code recordLoss()}, which already computes the resulting
 * balance internally for its FARELO-100/101 threshold check, {@code
 * create()} deliberately never does, see {@code
 * publishStockThresholdEventIfNeeded}'s javadoc: a {@code PURCHASE} can
 * never newly cross into low/out-of-stock territory) — purely to duplicate a
 * value the ledger already derives on demand, and for free, via {@code GET
 * /api/v1/ingredients/{id}/balance}. Paying that cost on every audited write
 * (not just on the rare read of the audit trail) to save a reviewer one
 * extra lookup isn't a trade this ticket's own text asks for, and doing it
 * only for {@code recordLoss()} (where it would be nearly free) while
 * leaving it out of {@code create()} would make the two producers' audit
 * rows inconsistent in shape for no real reason — same "don't compute a
 * value speculatively for a consumer that doesn't need it yet" discipline
 * already established throughout this domain (e.g. {@code
 * Ingredient.criticalStock} deliberately not added alongside {@code
 * minimumStock}, FARELO-099). A future consumer that wants "the balance at
 * the moment of this audited change" can still get it — not from this
 * snapshot, but by reading the ledger itself up to (and including) {@code
 * AuditLog.createdAt}, which remains fully derivable exactly as the prompt
 * mestre (seção 13) requires.
 */
record InventoryMovementSnapshot(InventoryMovementType type, BigDecimal quantity) {

    static InventoryMovementSnapshot from(InventoryMovement movement) {
        return new InventoryMovementSnapshot(movement.getType(), movement.getQuantity());
    }

}
