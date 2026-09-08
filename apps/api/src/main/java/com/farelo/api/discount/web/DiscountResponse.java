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
 *
 * <p>{@code orderItemId} (FARELO-317): {@code null} for a comanda-wide
 * discount, the discounted {@code OrderItem}'s id otherwise — unlike
 * {@code appliedByUserId} above, this id IS exposed: a client needs it to
 * correlate this discount against the specific {@code
 * OrderItemResponse.id()} it already has (e.g. to show "-R$2,00" next to
 * that one line), not just display a human-readable label.
 */
public record DiscountResponse(
        UUID id,
        int commandNumber,
        UUID orderItemId,
        DiscountType type,
        BigDecimal percentage,
        BigDecimal originalAmount,
        BigDecimal discountedAmount,
        String reason,
        String appliedByUserName,
        OffsetDateTime createdAt) {

    public static DiscountResponse from(Discount discount) {
        UUID orderItemId = discount.getOrderItem() != null ? discount.getOrderItem().getId() : null;

        return new DiscountResponse(
                discount.getId(),
                discount.getCommand().getNumber(),
                orderItemId,
                discount.getType(),
                discount.getPercentage(),
                discount.getOriginalAmount(),
                discount.getDiscountedAmount(),
                discount.getReason(),
                discount.getAppliedByUserName(),
                discount.getCreatedAt());
    }

}
