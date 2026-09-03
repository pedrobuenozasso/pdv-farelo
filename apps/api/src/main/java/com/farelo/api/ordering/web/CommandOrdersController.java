package com.farelo.api.ordering.web;

import com.farelo.api.ordering.OrderService;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.rbac.RequireRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Exposes {@code GET /api/v1/commands/{number}/orders} (FARELO-055).
 *
 * <p>Placement note: this is a command-scoped URL, but lives in the
 * {@code ordering} domain ({@code com.farelo.api.ordering.web}) rather
 * than in {@code com.farelo.api.command.web.CommandController}. The
 * {@code ordering} domain already depends on {@code command} (
 * {@code Order.command}, {@code OrderService} calls {@code CommandService})
 * — keeping this controller here too means that dependency stays
 * one-directional. Putting it in {@code CommandController} would make
 * {@code command.web} depend on {@code ordering.OrderService}, a
 * cross-domain dependency in the opposite direction (AGENTS.md: "evitar
 * dependências cruzadas desnecessárias"). The URL path is independent of
 * which class handles it, so this costs nothing.
 *
 * <p><b>FARELO-124 — requires {@code ADMIN}/{@code MANAGER}/{@code
 * CASHIER}/{@code ATTENDANT}</b>: unlike {@link
 * com.farelo.api.command.web.CommandController#findByNumber}, this
 * endpoint is <b>not</b> a dependency of the customer-facing "Cardápio QR"
 * flow — {@code apps/web}'s {@code app/c/[commandNumber]} route never
 * calls it (verified against the frontend source, not assumed); its only
 * current caller is the internal {@code /pdv} screen
 * ({@code apps/web/src/app/pdv/page.tsx}), which shows a comanda's order
 * history (with prices) so staff can decide whether to deliver/cancel an
 * order or close the tab. Same role list as {@code CommandController}'s
 * {@code open()} and {@code OrderController}'s {@code
 * markAsDelivered}/{@code markAsCancelled} — the same front-of-house
 * persona that screen serves. {@code KITCHEN} is deliberately excluded:
 * the kitchen has its own dedicated view ({@code GET /api/v1/orders}, the
 * KDS queue), which doesn't need per-comanda billing detail.
 */
@RestController
@RequestMapping("/api/v1/commands")
public class CommandOrdersController {

    private final OrderService orderService;

    public CommandOrdersController(OrderService orderService) {
        this.orderService = orderService;
    }

    // {number} is the command's business identifier, same convention as
    // com.farelo.api.command.web.CommandController.
    @GetMapping("/{number}/orders")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER, UserRole.ATTENDANT})
    public List<OrderResponse> listByCommand(@PathVariable int number) {
        return orderService.listByCommand(number).stream()
                .map(OrderResponse::from)
                .toList();
    }

}
