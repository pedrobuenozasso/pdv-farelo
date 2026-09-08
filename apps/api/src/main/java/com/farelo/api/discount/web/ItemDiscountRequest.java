package com.farelo.api.discount.web;

import com.farelo.api.discount.DiscountType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for {@code POST
 * /api/v1/orders/{orderId}/items/{itemId}/discounts} (FARELO-317). Same
 * shape/validation as {@link DiscountRequest} minus the comanda-level
 * fields that don't apply here — {@code orderId}/{@code itemId} are path
 * variables, not body fields, since the item they name is exactly what
 * distinguishes this endpoint from {@code POST
 * /api/v1/commands/{number}/discounts}.
 */
public record ItemDiscountRequest(
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
