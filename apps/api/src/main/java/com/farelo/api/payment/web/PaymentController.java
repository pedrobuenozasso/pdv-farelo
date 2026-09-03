package com.farelo.api.payment.web;

import com.farelo.api.payment.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Exposes {@code GET /api/v1/commands/{number}/payments} (FARELO-140).
 *
 * <p><b>Placement note</b>: this is a command-scoped URL, but lives in the
 * new {@code payment} domain ({@code com.farelo.api.payment.web}), not in
 * {@code com.farelo.api.command.web.CommandController}. Same dependency-
 * direction reasoning {@code CommandOrdersController}'s javadoc already
 * documents for the analogous choice in {@code ordering} (see its javadoc):
 * {@code payment} already depends on {@code command} — {@link
 * com.farelo.api.payment.Payment#getCommand()} is a required {@code
 * @ManyToOne}, and {@link PaymentService} calls {@code CommandService} to
 * resolve a business-facing comanda {@code number} — so keeping this
 * controller here too means that dependency stays one-directional. Putting
 * it in {@code CommandController} instead would make {@code command.web}
 * depend on {@code payment.PaymentService}, a cross-domain dependency in
 * the opposite direction (AGENTS.md: "evitar dependências cruzadas
 * desnecessárias"). Unlike {@code CommandOrdersController} — which had an
 * existing sibling precedent to follow ({@code ordering} already depending
 * on {@code command}) — {@code payment} is a brand-new domain with no prior
 * controller placement to react to; this is that same reasoning applied
 * from scratch, not a copy of a decision made for a different reason. The
 * URL path is independent of which class handles it, so this costs
 * nothing.
 *
 * <p><b>No {@code @RequireRole}</b>: same "leave a domain's first read
 * endpoint unprotected" precedent already followed by {@code Notification}
 * (FARELO-110) and {@code AuditLog} (FARELO-125) at their own first
 * tickets — no dedicated RBAC-application ticket exists yet for {@code
 * payment} (contrast with {@code CommandOrdersController}, protected only
 * later, at FARELO-124, a separate numbered ticket).
 */
@RestController
@RequestMapping("/api/v1/commands")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // {number} is the command's business identifier, same convention as
    // com.farelo.api.command.web.CommandController /
    // com.farelo.api.ordering.web.CommandOrdersController.
    @GetMapping("/{number}/payments")
    public List<PaymentResponse> listByCommand(@PathVariable int number) {
        return paymentService.listByCommand(number).stream()
                .map(PaymentResponse::from)
                .toList();
    }

}
