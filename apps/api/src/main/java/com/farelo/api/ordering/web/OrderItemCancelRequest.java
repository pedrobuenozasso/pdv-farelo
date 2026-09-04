package com.farelo.api.ordering.web;

import com.farelo.api.ordering.OrderItemCancelReason;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/orders/{orderId}/items/{itemId}/cancel}
 * (FARELO-200/201).
 *
 * <p>{@code reason} is required — FARELO-201's whole point ("Todo
 * cancelamento deve exigir motivo"), one of {@link OrderItemCancelReason}'s
 * five fixed values. {@code description} is optional for every reason
 * except {@link OrderItemCancelReason#OTHER}, where the ticket requires it
 * ("Se for OTHER, exigir descrição") — enforced by {@link
 * #isDescriptionRequiredWhenReasonIsOther()}, a
 * {@code @AssertTrue}-backed cross-field check rather than a full custom
 * {@code @Constraint} annotation (unlike {@code CstCsosnMutuallyExclusive}):
 * this rule only ever applies to this one DTO, so the extra
 * annotation/validator-class machinery would be undue ceremony for a
 * single call site — {@code @AssertTrue} is Bean Validation's own built-in
 * mechanism for exactly a "this method must return true" cross-field rule,
 * and it still produces the same {@code 400}/{@code VALIDATION_ERROR}
 * shape as every other constraint on this endpoint.
 */
public record OrderItemCancelRequest(
        @NotNull(message = "reason é obrigatório") OrderItemCancelReason reason,
        @Size(max = 500, message = "description deve ter no máximo 500 caracteres")
        String description) {

    @AssertTrue(message = "description é obrigatório quando reason é OTHER")
    public boolean isDescriptionRequiredWhenReasonIsOther() {
        return reason != OrderItemCancelReason.OTHER
                || (description != null && !description.isBlank());
    }

}
