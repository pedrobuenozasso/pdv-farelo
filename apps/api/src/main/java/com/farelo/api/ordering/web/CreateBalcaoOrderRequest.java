package com.farelo.api.ordering.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for {@code POST /api/v1/orders/balcao} (FARELO-312) — a
 * walk-up counter sale with no comanda. Same shape as {@link
 * CreateOrderRequest} minus {@code commandNumber} (there is none — see
 * {@code com.farelo.api.ordering.Order}'s javadoc, "Design decision —
 * BALCAO"): {@code items} (required, reuses {@link OrderItemRequest}), the
 * same optional {@code customerName}/{@code customerPhone} snapshot pair
 * every order creation flow accepts, and the same optional {@code note}
 * (FARELO-316)/{@code printOnConfirm} (FARELO-319) fields — see {@link
 * CreateOrderRequest#printOnConfirmOrDefault()}'s javadoc for why that one
 * is a {@code Boolean} wrapper.
 */
public record CreateBalcaoOrderRequest(
        @NotEmpty @Valid List<OrderItemRequest> items,
        String customerName,
        String customerPhone,
        @Size(max = 500, message = "note deve ter no máximo 500 caracteres") String note,
        Boolean printOnConfirm) {

    public boolean printOnConfirmOrDefault() {
        return printOnConfirm == null || printOnConfirm;
    }

}
