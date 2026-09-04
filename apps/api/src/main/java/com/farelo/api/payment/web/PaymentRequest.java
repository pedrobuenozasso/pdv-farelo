package com.farelo.api.payment.web;

import com.farelo.api.payment.PaymentMethod;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/v1/commands/{number}/payments}
 * (FARELO-141, "Registrar pagamento manual"). Never expose the JPA entity
 * directly on the API (see AGENTS.md) — this is the boundary DTO.
 *
 * <p>No {@code commandNumber}/{@code command} field — comes from the URL
 * path, not the body (the endpoint is already scoped to one comanda), same
 * convention as {@link com.farelo.api.inventory.web.InventoryMovementRequest}
 * not repeating {@code ingredientId}.
 *
 * <p>{@code amount} must be strictly positive ({@code @Positive}, same
 * validation-layer pattern as {@code InventoryMovementRequest#quantity()}/
 * {@code InventoryLossRequest#quantity()}): a payment of zero or less isn't a
 * payment. Unlike those two, there is no server-side sign flip to reason
 * about here — {@link com.farelo.api.payment.Payment#getAmount()} stores
 * exactly this positive magnitude, verbatim.
 *
 * <p>{@code method} is required, {@code @NotNull} — a missing/explicit-{@code
 * null} JSON field deserializes to {@code null} on the record (an enum is
 * already a reference type, so there's no wrapper-vs-primitive gotcha to
 * consider here, same reasoning as {@link
 * com.farelo.api.catalog.web.ProductRequest}'s javadoc for {@code
 * productionStation}), which {@code @NotNull} then rejects.
 *
 * <p>{@code amountReceived} (FARELO-225, "Tratar troco em dinheiro") —
 * optional, the cash the customer physically handed over, when it's more
 * than {@code amount} (e.g. total R$90, cash received R$100). {@code
 * amount} keeps its exact pre-existing meaning ("valor efetivamente
 * aplicado" — the ticket's own wording — is still what {@link
 * com.farelo.api.payment.Payment#getAmount()} stores, unchanged); {@code
 * amountReceived} only feeds {@code PaymentController#record}'s {@code
 * changeGiven} calculation and is never itself persisted — a physical cash
 * handback isn't money the business received, so it has no place in the
 * append-only ledger (see {@link com.farelo.api.payment.Payment}'s
 * javadoc). Two cross-field rules, {@code @AssertTrue}-backed like {@code
 * OrderItemCancelRequest}'s {@code reason}/{@code description} rule rather
 * than a full custom {@code @Constraint} (same reasoning: applies to this
 * one DTO only): {@link #isAmountReceivedOnlyForCash()} (change only makes
 * sense for physical cash — PIX/card transactions settle for the exact
 * amount) and {@link #isAmountReceivedAtLeastAmount()} (received less than
 * applied would mean applying money that was never handed over).
 */
public record PaymentRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull PaymentMethod method,
        @Positive BigDecimal amountReceived) {

    @AssertTrue(message = "amountReceived só é permitido quando method é CASH")
    public boolean isAmountReceivedOnlyForCash() {
        return amountReceived == null || method == PaymentMethod.CASH;
    }

    @AssertTrue(message = "amountReceived deve ser maior ou igual a amount")
    public boolean isAmountReceivedAtLeastAmount() {
        return amountReceived == null || amount == null || amountReceived.compareTo(amount) >= 0;
    }

}
