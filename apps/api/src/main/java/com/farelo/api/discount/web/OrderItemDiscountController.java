package com.farelo.api.discount.web;

import com.farelo.api.discount.Discount;
import com.farelo.api.discount.DiscountService;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.AuthenticatedPrincipal;
import com.farelo.api.security.rbac.RequireRole;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Exposes {@code POST /api/v1/orders/{orderId}/items/{itemId}/discounts}
 * (FARELO-317, "Desconto por item") — the item-scoped counterpart to
 * {@link DiscountController#apply}.
 *
 * <p><b>A separate controller, not a new method on {@code
 * com.farelo.api.ordering.web.OrderController}</b>: same one-directional-
 * dependency reasoning {@link DiscountController}'s own javadoc documents
 * for its analogous placement choice — {@code discount} depends on {@code
 * ordering}/{@code command}/{@code security}, so this lives here rather
 * than making {@code ordering.web} depend on {@code discount}. Living in
 * {@code discount.web} (not {@code ordering.web}) despite the URL starting
 * with {@code /api/v1/orders} mirrors {@code DiscountController} itself,
 * whose URL starts with {@code /api/v1/commands} for the same reason.
 *
 * <p>Same {@code ADMIN}/{@code MANAGER}/{@code CASHIER} role list as
 * {@link DiscountController#apply} — applying an item-level discount is
 * the same class of cash-handling action as a comanda-level one.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderItemDiscountController {

    private final DiscountService discountService;

    public OrderItemDiscountController(DiscountService discountService) {
        this.discountService = discountService;
    }

    @PostMapping("/{orderId}/items/{itemId}/discounts")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER})
    public ResponseEntity<DiscountResponse> apply(
            @PathVariable UUID orderId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ItemDiscountRequest request,
            AuthenticatedPrincipal principal,
            UriComponentsBuilder uriComponentsBuilder) {
        Discount discount = switch (request.type()) {
            case FIXED_AMOUNT -> discountService.applyFixedAmountToItem(
                    orderId, itemId, request.amount(), request.reason(), principal.userId());
            case PERCENTAGE -> discountService.applyPercentageToItem(
                    orderId, itemId, request.percentage(), request.reason(), principal.userId());
        };

        // Same "no single-item GET handler, but the header still names the
        // correct URI" convention as DiscountController#apply.
        URI location = uriComponentsBuilder
                .path("/api/v1/commands/{number}/discounts/{id}")
                .buildAndExpand(discount.getCommand().getNumber(), discount.getId())
                .toUri();

        return ResponseEntity.created(location).body(DiscountResponse.from(discount));
    }

}
