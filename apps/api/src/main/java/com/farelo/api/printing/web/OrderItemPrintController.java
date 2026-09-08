package com.farelo.api.printing.web;

import com.farelo.api.printing.PrintJob;
import com.farelo.api.printing.PrintJobService;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.rbac.RequireRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import java.util.List;

/**
 * Exposes two order-scoped "resend to production" actions, both living
 * here rather than in {@code ordering.web.OrderController} — see
 * "Placement note" below.
 *
 * <p>{@code POST /api/v1/orders/{orderId}/items/{itemId}/resend}
 * (FARELO-318, "Renotificar item" — docs/gaps-sischef-pdv.md item 8):
 * queues one more {@code PENDING} kitchen ticket for a single {@link
 * com.farelo.api.ordering.OrderItem}, without reprinting the rest of the
 * order.
 *
 * <p>{@code POST /api/v1/orders/{orderId}/resend} (FARELO-319, "Enviar
 * novamente os itens para produção" — docs/gaps-sischef-pdv.md item 9):
 * the whole-order counterpart — reuses {@link
 * PrintJobService#createForOrder(java.util.UUID)} wholesale (the exact
 * same builder the automatic {@code OrderCreated} path uses), so it always
 * works regardless of the order's {@code printOnConfirm} flag (see that
 * flag's own javadoc on {@code
 * com.farelo.api.ordering.Order#isPrintOnConfirm()} for why a manual
 * resend must be unconditional).
 *
 * <p><b>Placement note</b>: an order-scoped URL living in {@code
 * printing.web}, not {@code ordering.web.OrderController} — same one-
 * directional-dependency reasoning {@link CommandPrintConferenceController}'s
 * javadoc already documents for its own analogous placement: {@code
 * printing} already depends on {@code ordering} ({@link PrintJobService}
 * calls {@code OrderService#getItem}/reads {@code Order}/{@code
 * OrderItem}), so keeping this controller here keeps that dependency
 * one-directional instead of making {@code ordering.web} depend back on
 * {@code printing.PrintJobService}.
 *
 * <p><b>Role list</b>: same as {@code OrderController#cancelItem} — asking
 * the kitchen to reprint (one item, or the whole order) is the same class
 * of front-of-house action, made from the same PDV screen.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderItemPrintController {

    private final PrintJobService printJobService;
    private final ObjectMapper objectMapper;

    public OrderItemPrintController(PrintJobService printJobService, ObjectMapper objectMapper) {
        this.printJobService = printJobService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{orderId}/items/{itemId}/resend")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER, UserRole.ATTENDANT})
    public PrintJobResponse resend(@PathVariable UUID orderId, @PathVariable UUID itemId) {
        PrintJob job = printJobService.createForOrderItem(orderId, itemId);
        return PrintJobResponse.from(job, objectMapper);
    }

    @PostMapping("/{orderId}/resend")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER, UserRole.ATTENDANT})
    public List<PrintJobResponse> resendAll(@PathVariable UUID orderId) {
        return printJobService.createForOrder(orderId).stream()
                .map(job -> PrintJobResponse.from(job, objectMapper))
                .toList();
    }

}
