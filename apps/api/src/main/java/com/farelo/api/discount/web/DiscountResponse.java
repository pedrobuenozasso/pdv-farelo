package com.farelo.api.discount.web;

import com.farelo.api.discount.Discount;
import com.farelo.api.discount.DiscountType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body exposing only the public fields of {@link Discount} — the
 * JPA entity itself is never returned by the API (see AGENTS.md).
 * {@code appliedByUserId} deliberately NOT exposed — same "id is an
 * internal detail, only the human-readable name is" reasoning {@code
 * OrderItemResponse} already established for {@code cancelledByUserId}.
 */
public record DiscountResponse(
        UUID id,
        int commandNumber,
        DiscountType type,
        BigDecimal percentage,
        BigDecimal originalAmount,
        BigDecimal discountedAmount,
        String reason,
        String appliedByUserName,
        OffsetDateTime createdAt) {

    public static DiscountResponse from(Discount discount) {
        return new DiscountResponse(
                discount.getId(),
                discount.getCommand().getNumber(),
                discount.getType(),
                discount.getPercentage(),
                discount.getOriginalAmount(),
                discount.getDiscountedAmount(),
                discount.getReason(),
                discount.getAppliedByUserName(),
                discount.getCreatedAt());
    }

}
