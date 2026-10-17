package com.farelo.api.ordering.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

/**
 * One line item in {@link CreateOrderRequest}.
 *
 * <p>{@code quantity} stays a primitive {@code int} (not a wrapper, unlike
 * decisions elsewhere in this codebase) — a JSON body omitting it defaults
 * to {@code 0} via Jackson, and {@code 0} already fails {@code @Positive},
 * so the primitive-default gotcha (documented on
 * {@code com.farelo.api.catalog.web.ProductUpdateRequest}) doesn't apply
 * here: a missing value is correctly rejected either way.
 */
public record OrderItemRequest(
        @NotNull UUID productId,
        @Positive int quantity,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<@NotNull @Valid OrderOptionRequest> options) {
}
