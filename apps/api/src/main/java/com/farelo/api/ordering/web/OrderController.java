package com.farelo.api.ordering.web;

import com.farelo.api.ordering.NewOrderItem;
import com.farelo.api.ordering.OrderItem;
import com.farelo.api.ordering.OrderService;
import com.farelo.api.ordering.OrderWithItems;
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
import java.util.UUID;

/**
 * {@code /api/v1/orders} — every method here except {@link #create} now
 * requires a staff role as of FARELO-124; see each method's javadoc for
 * exactly which role(s) and why.
 *
 * <p><b>{@link #create} stays deliberately unprotected</b> — it's the
 * single most important public dependency in this whole ticket's scope:
 * the customer-facing "Cardápio QR" checkout
 * ({@code apps/web/src/app/c/[commandNumber]/menu.tsx}, no login of any
 * kind — prompt mestre seção 6, "Confirma → Order criado") calls exactly
 * this endpoint to place an order. Protecting it would break the product's
 * entire ordering flow, not narrow it to a more appropriate staff role —
 * the same "public dependency, don't lock it down" reasoning FARELO-123
 * used for {@code GET /api/v1/categories}/{@code GET /api/v1/products},
 * just on a {@code POST} instead of a {@code GET} this time (verified
 * against the actual frontend call site, not assumed from the endpoint's
 * HTTP method alone).
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            UriComponentsBuilder uriComponentsBuilder) {
        List<NewOrderItem> items = request.items().stream()
                .map(item -> new NewOrderItem(item.productId(), item.quantity()))
                .toList();

        OrderWithItems result = orderService.create(
                request.commandNumber(), items, request.customerName(), request.customerPhone());

        // No GET /api/v1/orders/{id} yet (future ticket) — the Location
        // header still names the resource's URI, which is correct even
        // before a handler exists to resolve it.
        URI location = uriComponentsBuilder
                .path("/api/v1/orders/{id}")
                .buildAndExpand(result.order().getId())
                .toUri();

        return ResponseEntity.created(location).body(OrderResponse.from(result));
    }

    // Kitchen queue (FARELO-059): every order across every command that
    // still needs kitchen attention (CREATED/CONFIRMED/PREPARING), oldest
    // first. Conceptually a `kitchen` domain concern (see docs/domain-model
    // .md), but it's a single read endpoint reusing OrderService/Order
    // entirely — putting it here reuses the existing `/api/v1/orders` root
    // resource controller instead of standing up a new `kitchen` package
    // for one endpoint (AGENTS.md: no abstração prematura). Revisit if/when
    // `kitchen` gains real responsibilities of its own (KDS printing, etc).
    //
    // FARELO-124: ADMIN/MANAGER/KITCHEN — this is the KDS's queue
    // (apps/web/src/app/kds/page.tsx is its only current caller), so
    // KITCHEN is the natural staff role. Unlike markAsPreparing/markAsReady
    // below, CASHIER isn't included here: no current screen shows this
    // queue to front-of-house staff, and widening a read endpoint "just in
    // case" without a real consumer would be guessing ahead of a need, the
    // same restraint FARELO-123 documented for narrower-than-expected role
    // lists elsewhere.
    @GetMapping
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.KITCHEN})
    public List<OrderResponse> queue() {
        return orderService.listQueue().stream()
                .map(OrderResponse::from)
                .toList();
    }

    // POST, not PATCH — same reasoning as
    // com.farelo.api.command.web.CommandController's /open, /close: this
    // is an action, not a partial representation update.
    //
    // FARELO-124: ADMIN/MANAGER/KITCHEN/CASHIER. KITCHEN is the obvious
    // role — apps/web/src/app/kds/page.tsx is the only current caller of
    // this transition. CASHIER is added deliberately, not as a blanket
    // "add every role" move: Order (and this endpoint) isn't scoped to a
    // single production station the way PrintJob is (see FARELO-074) — an
    // order can mix BAR items (often made by whoever is working the
    // counter, i.e. CASHIER in a small café) and KITCHEN items in the same
    // Order, and this transition operates at the whole-order level, not
    // per item/station. So a cashier plausibly needs to mark an
    // order they're personally preparing (e.g. all-BAR items) as
    // PREPARING/READY without a kitchen account. ATTENDANT is
    // deliberately excluded: table service/delivery, not food/drink
    // preparation.
    @PostMapping("/{id}/preparing")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.KITCHEN, UserRole.CASHIER})
    public OrderResponse markAsPreparing(@PathVariable UUID id) {
        OrderWithItems result = orderService.markAsPreparing(id);
        return OrderResponse.from(result);
    }

    // Same role list and reasoning as markAsPreparing above — whoever can
    // start preparing an order can also mark it ready; splitting the two
    // into different role sets would mean a cashier could start making a
    // BAR item but never be allowed to say it's done, which doesn't match
    // how the actual workflow works.
    @PostMapping("/{id}/ready")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.KITCHEN, UserRole.CASHIER})
    public OrderResponse markAsReady(@PathVariable UUID id) {
        OrderWithItems result = orderService.markAsReady(id);
        return OrderResponse.from(result);
    }

    // FARELO-124: ADMIN/MANAGER/CASHIER/ATTENDANT — handing a READY order
    // to the customer at their table/the counter is a front-of-house
    // action, not a kitchen one, so KITCHEN is deliberately excluded here
    // (opposite of markAsPreparing/markAsReady above, which exclude
    // ATTENDANT instead). Matches this endpoint's actual current caller,
    // apps/web/src/app/pdv/page.tsx (OrderCard's "Marcar como entregue"),
    // a staff-facing screen for exactly this kind of floor-service action.
    @PostMapping("/{id}/deliver")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER, UserRole.ATTENDANT})
    public OrderResponse markAsDelivered(@PathVariable UUID id) {
        OrderWithItems result = orderService.markAsDelivered(id);
        return OrderResponse.from(result);
    }

    // FARELO-124: same role list as markAsDelivered above, same current
    // caller (apps/web/src/app/pdv/page.tsx's OrderCard, "Cancelar
    // pedido") — cancelling an order is a comanda/order-lifecycle decision
    // made from the same front-of-house screen as delivering one, not a
    // kitchen action, so KITCHEN is excluded for the same reason.
    @PostMapping("/{id}/cancel")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER, UserRole.ATTENDANT})
    public OrderResponse markAsCancelled(@PathVariable UUID id) {
        OrderWithItems result = orderService.markAsCancelled(id);
        return OrderResponse.from(result);
    }

    // FARELO-200/201: same role list as markAsCancelled above — cancelling
    // one line is the same class of front-of-house decision as cancelling
    // the whole order, made from the same pdv/page.tsx OrderCard. Needs
    // AuthenticatedPrincipal (unlike markAsCancelled) so OrderService#
    // cancelItem can denormalize who performed the cancellation onto the
    // item (see OrderItem#cancel's javadoc) — resolved the same way
    // ProductController/InventoryMovementController already do for their
    // @RequireRole-protected write endpoints.
    @PostMapping("/{orderId}/items/{itemId}/cancel")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER, UserRole.ATTENDANT})
    public OrderItemResponse cancelItem(
            @PathVariable UUID orderId,
            @PathVariable UUID itemId,
            @Valid @RequestBody OrderItemCancelRequest request,
            AuthenticatedPrincipal principal) {
        OrderItem item = orderService.cancelItem(
                orderId, itemId, request.reason(), request.description(), principal.userId());
        return OrderItemResponse.from(item);
    }

}
