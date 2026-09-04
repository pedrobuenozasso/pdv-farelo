package com.farelo.api.discount;

/**
 * How a {@link Discount} was computed (FARELO-230/231): {@code
 * FIXED_AMOUNT} — a flat reduction (e.g. "-R$10,00"); {@code PERCENTAGE} —
 * a rate applied against the comanda's {@code totalOwed} at the moment of
 * application (e.g. "10%"). {@code @Enumerated(EnumType.STRING)} on {@link
 * Discount#getType()}, never {@code ORDINAL} — same reasoning as every
 * other status/type enum in this codebase.
 */
public enum DiscountType {
    FIXED_AMOUNT,
    PERCENTAGE
}
