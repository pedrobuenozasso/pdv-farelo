package com.farelo.api.ordering;

/**
 * The fixed, closed set of reasons an {@link OrderItem} can be cancelled
 * for (FARELO-201, "Motivo obrigatório no cancelamento") — verbatim from
 * the ticket's own list, not invented. {@link #OTHER} is the one value
 * that requires {@code OrderItem#getCancelDescription()} to be non-blank
 * (see {@code OrderItemCancelRequest}'s javadoc and migration
 * V35's {@code ck_order_item_other_reason_requires_description} CHECK);
 * every other value stands on its own with no free-text required.
 *
 * <p>{@code EnumType.STRING}, never {@code ORDINAL} — same reasoning as
 * every other enum in this codebase (storing the ordinal would silently
 * corrupt data if this enum's declaration order ever changes).
 */
public enum OrderItemCancelReason {
    CUSTOMER_REQUEST,
    ENTRY_ERROR,
    OUT_OF_STOCK,
    QUALITY_ISSUE,
    OTHER
}
