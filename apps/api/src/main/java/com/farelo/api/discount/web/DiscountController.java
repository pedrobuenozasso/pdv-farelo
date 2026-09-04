package com.farelo.api.discount.web;

import com.farelo.api.discount.Discount;
import com.farelo.api.discount.DiscountService;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.AuthenticatedPrincipal;
import com.farelo.api.security.rbac.RequireRole;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Exposes {@code GET}/{@code POST /api/v1/commands/{number}/discounts}
 * (FARELO-230/231/232).
 *
 * <p><b>{@link #apply} requires {@code ADMIN}/{@code MANAGER}/{@code
 * CASHIER}</b> — same role list {@code PaymentController#record} uses:
 * applying a discount is the same class of cash-handling/financial
 * adjustment action, so it reuses that precedent rather than inventing a
 * new one. {@code ATTENDANT}/{@code KITCHEN} excluded for the same reason
 * {@code PaymentController} excludes them. Needs {@link
 * AuthenticatedPrincipal} to denormalize who applied the discount onto
 * the record (see {@code Discount}'s javadoc) — resolved the same way
 * {@code OrderController#cancelItem} already does for its own
 * actor-denormalization need.
 *
 * <p><b>{@link #listByCommand} stays unprotected</b>, same "a domain's
 * read endpoint over its own ledger carries no more sensitivity than the
 * write it's computed from" precedent {@code PaymentController#listByCommand}
 * already follows.
 *
 * <p><b>Placement note</b>: {@code discount} depends on {@code command}/
 * {@code ordering}/{@code security} ({@link DiscountService}), so this
 * controller lives in {@code discount.web} rather than making {@code
 * command.web} depend on it — same one-directional-dependency reasoning
 * {@code PaymentController}'s own javadoc documents for its analogous
 * choice.
 */
@RestController
@RequestMapping("/api/v1/commands")
public class DiscountController {

    private final DiscountService discountService;

    public DiscountController(DiscountService discountService) {
        this.discountService = discountService;
    }

    @GetMapping("/{number}/discounts")
    public List<DiscountResponse> listByCommand(@PathVariable int number) {
        return discountService.listByCommand(number).stream()
                .map(DiscountResponse::from)
                .toList();
    }

    // Location points at .../discounts/{id} — same convention
    // PaymentController#record/OrderController#create use: no single-item
    // GET handler exists for this resource, but the header still names the
    // correct URI under the REST collection/{id} convention.
    @PostMapping("/{number}/discounts")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER})
    public ResponseEntity<DiscountResponse> apply(
            @PathVariable int number,
            @Valid @RequestBody DiscountRequest request,
            AuthenticatedPrincipal principal,
            UriComponentsBuilder uriComponentsBuilder) {
        Discount discount = switch (request.type()) {
            case FIXED_AMOUNT -> discountService.applyFixedAmount(
                    number, request.amount(), request.reason(), principal.userId());
            case PERCENTAGE -> discountService.applyPercentage(
                    number, request.percentage(), request.reason(), principal.userId());
        };

        URI location = uriComponentsBuilder
                .path("/api/v1/commands/{number}/discounts/{id}")
                .buildAndExpand(number, discount.getId())
                .toUri();

        return ResponseEntity.created(location).body(DiscountResponse.from(discount));
    }

}
