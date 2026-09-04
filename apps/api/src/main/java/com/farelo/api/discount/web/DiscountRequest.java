package com.farelo.api.discount.web;

import com.farelo.api.discount.DiscountType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/v1/commands/{number}/discounts}
 * (FARELO-230/231). Never expose the JPA entity directly (see AGENTS.md).
 *
 * <p>One endpoint for both discount shapes, {@code type} discriminating
 * which of {@code amount}/{@code percentage} applies — same single-request/
 * discriminator-field convention {@code PaymentRequest#method} already
 * established, rather than two separate endpoints: applying a discount is
 * one action with two possible inputs, not two different actions.
 * {@link #isFixedAmountShapeValid()}/{@link #isPercentageShapeValid()}
 * enforce that exactly the matching field is populated, {@code
 * @AssertTrue}-backed like {@code PaymentRequest#amountReceived}'s rules
 * rather than a full custom {@code @Constraint} (same reasoning: applies
 * to this one DTO only).
 *
 * <p>{@code percentage} is capped at 100 ({@code @DecimalMax}) — a discount
 * greater than the whole bill makes no sense as a rate (unlike {@code
 * amount}, which is deliberately uncapped against the comanda's total, see
 * {@code DiscountService#applyFixedAmount}'s javadoc).
 *
 * <p>{@code reason} (FARELO-232, "motivo do desconto") is optional —
 * deliberately not required, even conditionally. The ticket's own wording
 * ("registrar opcionalmente ou obrigatoriamente conforme configuração")
 * implies a system-wide setting choosing which; no general configuration
 * mechanism exists anywhere in this codebase to hang that toggle on (the
 * one config-shaped entity, {@code CompanyFiscalConfiguration}, is fiscal-
 * specific, not a general settings store), and inventing one here — with
 * no second consumer yet needing it — would be exactly the kind of
 * speculative abstraction this codebase avoids elsewhere (AGENTS.md). The
 * field is optional today; a future ticket can add the toggle once a real
 * "make reason mandatory" requirement (and a place to configure it) exists.
 * {@code @Size(max = 500)} caps it at the same length {@code
 * OrderItemCancelRequest#description} uses, for consistency.
 */
public record DiscountRequest(
        @NotNull DiscountType type,
        @Positive BigDecimal amount,
        @Positive @DecimalMax("100") BigDecimal percentage,
        @Size(max = 500, message = "reason deve ter no máximo 500 caracteres") String reason) {

    @AssertTrue(message = "type FIXED_AMOUNT requer amount e não aceita percentage")
    public boolean isFixedAmountShapeValid() {
        return type != DiscountType.FIXED_AMOUNT || (amount != null && percentage == null);
    }

    @AssertTrue(message = "type PERCENTAGE requer percentage e não aceita amount")
    public boolean isPercentageShapeValid() {
        return type != DiscountType.PERCENTAGE || (percentage != null && amount == null);
    }

}
