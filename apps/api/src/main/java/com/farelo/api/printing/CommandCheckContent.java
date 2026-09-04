package com.farelo.api.printing;

import com.farelo.api.command.Command;
import com.farelo.api.ordering.OrderItem;

import java.math.BigDecimal;
import java.util.List;

/**
 * The shape serialized into {@link PrintJob#getContent()} for a {@link
 * PrintJobType#COMMAND_CHECK} job (FARELO-211) — the "conferência" printed
 * counterpart to {@link PrintJobContent} (kitchen tickets). Same frozen-
 * snapshot reasoning as that record's javadoc, "Design decision 2": what
 * the customer is being asked to check must reflect reality at the moment
 * the conferência was requested, not whatever the comanda says by the time
 * the Edge Agent gets around to printing it.
 *
 * <p>Unlike {@link PrintJobContent} (product name + quantity only — a
 * kitchen ticket doesn't need price), a conferência is a bill preview, so
 * each {@link Item} carries {@code unitPrice}/{@code lineTotal} and the
 * whole content carries a {@code total} — the same figure {@code
 * OrderItemRepository#sumOwedByCommand} would compute (Single Source of
 * Truth for the comanda's total, per the project's own rule), reused via
 * {@link #from} rather than re-implemented here.
 *
 * <p>Built by {@link PrintJobService#createCommandCheck(int)} from every
 * non-cancelled {@link OrderItem} of every non-{@code CANCELLED} order
 * belonging to the {@link Command} — same exclusion FARELO-200/201 already
 * established for what counts toward what's owed, reused here instead of
 * a second, potentially-drifting definition of "billable items".
 */
public record CommandCheckContent(int commandNumber, List<Item> items, BigDecimal total) {

    public record Item(String productName, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    public static CommandCheckContent from(Command command, List<OrderItem> items) {
        List<Item> printItems = items.stream()
                .map(item -> new Item(
                        item.getProduct().getName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))))
                .toList();

        BigDecimal total = printItems.stream()
                .map(Item::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CommandCheckContent(command.getNumber(), printItems, total);
    }

}
