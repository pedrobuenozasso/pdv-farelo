package com.farelo.api.printing;

/**
 * What a {@link PrintJob} prints (FARELO-210): {@code KITCHEN_TICKET} is
 * the original kind (FARELO-071), scoped to one {@link
 * com.farelo.api.ordering.Order} — see {@link PrintJob}'s javadoc.
 * {@code COMMAND_CHECK} is the "conferência" — a customer-facing pre-bill
 * for a whole {@link com.farelo.api.command.Command}, listing every
 * non-cancelled item across every non-cancelled order with prices and a
 * total (see {@link CommandCheckContent}), instead of the item
 * names/quantities a kitchen ticket needs.
 *
 * <p>{@code @Enumerated(EnumType.STRING)} on {@link PrintJob#getType()},
 * never {@code ORDINAL} — same reasoning as every other status/type enum
 * in this codebase (e.g. {@link PrintJobStatus}): storing the ordinal
 * would silently corrupt data if this enum's declaration order ever
 * changes.
 */
public enum PrintJobType {
    KITCHEN_TICKET,
    COMMAND_CHECK
}
