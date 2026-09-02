package com.farelo.api.inventory;

/**
 * The unit of measure an {@link Ingredient}'s stock is tracked in.
 *
 * <p><b>FARELO-090 scope</b>: the prompt mestre (seção 14) lists {@code UN},
 * {@code G}, {@code KG}, {@code ML}, {@code L} as the ingredient units, plus
 * purchase-unit conversion (e.g. "1 bandeja de ovo = 30 UN") on top of an
 * internal base unit. None of that is needed yet — this ticket only creates
 * {@link Ingredient} itself, with no {@code Recipe}/{@code InventoryMovement}
 * consuming it (those are FARELO-091+). Modeling the full unit list and a
 * conversion mechanism now would be guessing a design for a consumer that
 * doesn't exist yet (AGENTS.md: não criar abstrações prematuras).
 *
 * <p>This enum instead covers only the three units actually distinguishable
 * for a base/internal unit of measure: {@code GRAM} and {@code MILLILITER}
 * (continuous quantities, mass and volume) and {@code UNIT} (discrete items
 * like cups or packaging). {@code KG}/{@code L} from the prompt mestre are
 * purchase/display units, not base units — the same weight is just
 * {@code GRAM} at a different scale (1 KG = 1000 G), which is exactly the
 * "purchase unit conversion" the prompt mestre says to keep separate from the
 * stock unit ("não misturar unidade de estoque com descrição de embalagem —
 * internamente preferir unidade base"). Modeling `KG`/`L` as their own stock
 * unit today would force every future ledger entry
 * ({@code InventoryMovement}, FARELO-093) to convert between mixed units
 * before summing a balance; a single base unit per ingredient avoids that
 * entirely. Extensible later (e.g. a dedicated purchase-unit/conversion
 * concept) if a real ticket needs it.
 */
public enum IngredientUnit {
    GRAM,
    MILLILITER,
    UNIT
}
