package com.farelo.api.payment.web;

import com.farelo.api.command.Command;
import com.farelo.api.command.web.CommandResponse;
import com.farelo.api.payment.Payment;
import com.farelo.api.payment.PaymentService;
import com.farelo.api.security.UserRole;
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

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

/**
 * Exposes {@code GET /api/v1/commands/{number}/payments} (FARELO-140) and
 * {@code POST /api/v1/commands/{number}/payments} (FARELO-141, "Registrar
 * pagamento manual").
 *
 * <p><b>{@link #record} requires {@code ADMIN}/{@code MANAGER}/{@code
 * CASHIER}</b>: unlike {@link #listByCommand} below, this is a brand-new
 * write surface with no unprotected-by-precedent read endpoint to defer to
 * (contrast {@code InventoryMovementController}, where FARELO-127 had to
 * weigh protecting an *existing* write endpoint against the scope of a
 * differently-named ticket — not the situation here: FARELO-141 *is* the
 * ticket that creates this write surface, so applying RBAC to it now is
 * squarely in scope, not a tangent). Recording a payment is unambiguously a
 * cash-handling action, so this reuses the exact role list {@link
 * com.farelo.api.command.web.CommandController#close} already settled on for
 * the closest precedent of "a cash-handling comanda action" (see that
 * method's javadoc): {@code ADMIN}/{@code MANAGER}/{@code CASHIER}, and
 * deliberately <b>not</b> {@code ATTENDANT} (front-of-house table service,
 * not cash handling — same exclusion {@code close()} makes) or {@code
 * KITCHEN} (no business recording payments at all). No {@code
 * AuthenticatedPrincipal} parameter is needed here, unlike {@code
 * InventoryMovementController#create}/{@code #recordLoss}: those endpoints
 * need a principal because they feed an audit trail
 * ({@code InventoryMovementService#recordAudit}); {@link Payment} carries no
 * "recorded by" field (this ticket doesn't add one — see {@code Payment}'s
 * javadoc, its shape is deliberately unchanged), so there is nothing for a
 * principal to be forwarded to.
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
 * <p><b>{@link #listByCommand} stays unprotected</b>: same "leave a domain's
 * first read endpoint unprotected" precedent already followed by {@code
 * Notification} (FARELO-110) and {@code AuditLog} (FARELO-125) at their own
 * first tickets, and unaffected by {@link #record} landing above — read
 * access to the ledger carries none of the cash-handling-write concern that
 * justifies protecting {@code record} (same split {@code
 * InventoryMovementController} already has between its protected {@code
 * POST}s and unprotected {@code GET}s).
 *
 * <p><b>{@link #totalPaid} (FARELO-142) also stays unprotected</b>, same
 * reasoning as {@link #listByCommand}: it's a read over the same ledger,
 * exposing a derived aggregate rather than raw rows — no more sensitive
 * than the list it's computed from.
 *
 * <p><b>{@link #close} (FARELO-143) — moved here from {@code
 * com.farelo.api.command.web.CommandController}.</b> {@code POST
 * /api/v1/commands/{number}/close} is still the exact same URL FARELO-034
 * introduced, but the handler for it now lives in this class, calling
 * {@link PaymentService#closeCommand(int)} instead of {@code
 * CommandService#close(int)} directly — see that method's javadoc for the
 * full dependency-direction reasoning on why the payment-sufficiency check
 * had to live in {@code payment}, and why keeping the controller mapping
 * consistent with that (rather than leaving it in {@code CommandController}
 * and having {@code command.web} depend on {@code PaymentService}) was the
 * more consistent choice given this class's own placement reasoning above.
 * Reuses {@link CommandResponse} — the response shape for {@code close} is
 * unchanged by this move, still the same {@code Command} representation
 * {@code open}/{@code findByNumber} return. Same {@code ADMIN}/{@code
 * MANAGER}/{@code CASHIER} role list {@code CommandController#close} always
 * used (cash-handling action, no {@code ATTENDANT}/{@code KITCHEN}) — RBAC
 * behavior is unchanged, only the class that enforces it.
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

    // FARELO-141. Location points at .../payments/{id} — same convention
    // OrderController#create/InventoryMovementController#create/#recordLoss
    // already use: this resource has no single-item GET handler of its own
    // (only the list above), but the Location header still names the
    // resource's correct URI under the REST collection/{id} convention, even
    // before any handler exists to resolve it (see OrderController#create's
    // comment for the same reasoning spelled out in full).
    @PostMapping("/{number}/payments")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER})
    public ResponseEntity<PaymentResponse> record(
            @PathVariable int number,
            @Valid @RequestBody PaymentRequest request,
            UriComponentsBuilder uriComponentsBuilder) {
        Payment payment = paymentService.record(number, request.amount(), request.method());

        URI location = uriComponentsBuilder
                .path("/api/v1/commands/{number}/payments/{id}")
                .buildAndExpand(number, payment.getId())
                .toUri();

        return ResponseEntity.created(location).body(PaymentResponse.from(payment));
    }

    // FARELO-142 ("Permitir múltiplos pagamentos por comanda"). A separate
    // sibling endpoint under the same .../payments collection, rather than
    // reshaping listByCommand's existing response — the least disruptive
    // way to expose "total paid" without breaking any existing consumer of
    // the plain array GET .../payments already returns. Same "dedicated
    // endpoint for a derived aggregate, separate from the ledger listing"
    // shape as GET /api/v1/ingredients/{ingredientId}/balance (FARELO-095)
    // — see PaymentService#getTotalPaid's javadoc for the full reasoning,
    // including why this does not also expose "total owed"/"fully paid".
    @GetMapping("/{number}/payments/total")
    public PaymentTotalResponse totalPaid(@PathVariable int number) {
        BigDecimal totalPaid = paymentService.getTotalPaid(number);
        return PaymentTotalResponse.of(number, totalPaid);
    }

    // FARELO-143 ("Validar total pago antes de fechar") — see this class's
    // javadoc for why this route moved here from CommandController, and
    // PaymentService#closeCommand's javadoc for the full validation
    // reasoning. Same POST-not-PATCH and Location-header conventions don't
    // apply here (this isn't a creation endpoint); URL/method/role list are
    // byte-for-byte what CommandController#close used before this ticket.
    @PostMapping("/{number}/close")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER})
    public CommandResponse close(@PathVariable int number) {
        Command command = paymentService.closeCommand(number);
        return CommandResponse.from(command);
    }

}
