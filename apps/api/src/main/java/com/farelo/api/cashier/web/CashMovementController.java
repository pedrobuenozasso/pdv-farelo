package com.farelo.api.cashier.web;

import com.farelo.api.cashier.CashMovement;
import com.farelo.api.cashier.CashMovementService;
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
 * {@code /api/v1/cash-sessions/{id}/...} — {@link #recordWithdrawal}
 * (sangria) and {@link #recordSupply} (suprimento) (FARELO-310), {@link
 * #recordExpense} (despesa) and {@link #recordIncome} (receita)
 * (FARELO-311), plus {@link #list} (backing {@code GET .../movements}).
 * Same URL-prefix
 * placement as {@code CashSessionController} (this stays inside {@code
 * cashier.web}, not a separate top-level path) — {@link CashMovement}
 * already depends on {@code CashSession} ({@code @ManyToOne}), so keeping
 * the controller here too keeps that dependency direction one-way, same
 * reasoning {@code PaymentController}'s javadoc documents for its own
 * placement relative to {@code command}.
 *
 * <p><b>Four dedicated endpoints (two here, two more in FARELO-311), not
 * one generic {@code POST .../movements} with a {@code type} field</b> —
 * unlike {@code DiscountRequest#type}, which discriminates between two
 * shapes of the <em>same</em> user action ("apply a discount"), sangria/
 * suprimento/despesa/receita are four <em>different</em> actions a cashier
 * picks explicitly in the UI (per {@code docs/gaps-sischef-pdv.md}, each
 * with its own button, not a shared form with a dropdown) — a dedicated
 * route per action keeps access logs/APM self-describing by path alone,
 * without needing to inspect the request body.
 *
 * <p><b>{@link #recordWithdrawal}/{@link #recordSupply} require {@code
 * ADMIN}/{@code MANAGER}/{@code CASHIER}</b> — same cash-handling role list
 * {@code CashSessionController#open}/{@code #close} already use; unlike
 * {@code #reconcile} (a supervisory check over the operator's own work),
 * sangria/suprimento are actions the operator performs themselves day to
 * day. {@link #list} allows the same three roles, same "read over the same
 * operational data" reasoning as {@code CashSessionController#list}.
 */
@RestController
@RequestMapping("/api/v1/cash-sessions")
public class CashMovementController {

    private final CashMovementService cashMovementService;

    public CashMovementController(CashMovementService cashMovementService) {
        this.cashMovementService = cashMovementService;
    }

    @PostMapping("/{id}/withdrawals")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER})
    public ResponseEntity<CashMovementResponse> recordWithdrawal(
            @PathVariable UUID id,
            @Valid @RequestBody CashMovementRequest request,
            AuthenticatedPrincipal principal,
            UriComponentsBuilder uriComponentsBuilder) {
        CashMovement movement = cashMovementService.recordWithdrawal(
                id, request.amount(), request.description(), principal.userId(), principal.role());
        return created(movement, id, uriComponentsBuilder);
    }

    @PostMapping("/{id}/supplies")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER})
    public ResponseEntity<CashMovementResponse> recordSupply(
            @PathVariable UUID id,
            @Valid @RequestBody CashMovementRequest request,
            AuthenticatedPrincipal principal,
            UriComponentsBuilder uriComponentsBuilder) {
        CashMovement movement = cashMovementService.recordSupply(
                id, request.amount(), request.description(), principal.userId(), principal.role());
        return created(movement, id, uriComponentsBuilder);
    }

    // FARELO-311. Same role list/shape as recordWithdrawal/recordSupply
    // above — despesa is an action the operator performs themselves.
    @PostMapping("/{id}/expenses")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER})
    public ResponseEntity<CashMovementResponse> recordExpense(
            @PathVariable UUID id,
            @Valid @RequestBody CashMovementRequest request,
            AuthenticatedPrincipal principal,
            UriComponentsBuilder uriComponentsBuilder) {
        CashMovement movement = cashMovementService.recordExpense(
                id, request.amount(), request.description(), principal.userId(), principal.role());
        return created(movement, id, uriComponentsBuilder);
    }

    // FARELO-311. Same role list/shape as recordWithdrawal/recordSupply
    // above — receita is an action the operator performs themselves.
    @PostMapping("/{id}/incomes")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER})
    public ResponseEntity<CashMovementResponse> recordIncome(
            @PathVariable UUID id,
            @Valid @RequestBody CashMovementRequest request,
            AuthenticatedPrincipal principal,
            UriComponentsBuilder uriComponentsBuilder) {
        CashMovement movement = cashMovementService.recordIncome(
                id, request.amount(), request.description(), principal.userId(), principal.role());
        return created(movement, id, uriComponentsBuilder);
    }

    // Location header points at .../movements/{id} — same convention
    // PaymentController#record already uses: no single-item GET handler
    // exists for this resource (only the list below), but the Location
    // header still names the resource's correct URI under the REST
    // collection/{id} convention.
    private ResponseEntity<CashMovementResponse> created(
            CashMovement movement, UUID cashSessionId, UriComponentsBuilder uriComponentsBuilder) {
        URI location = uriComponentsBuilder
                .path("/api/v1/cash-sessions/{id}/movements/{movementId}")
                .buildAndExpand(cashSessionId, movement.getId())
                .toUri();
        return ResponseEntity.created(location).body(CashMovementResponse.from(movement));
    }

    @GetMapping("/{id}/movements")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER})
    public List<CashMovementResponse> list(@PathVariable UUID id, AuthenticatedPrincipal principal) {
        return cashMovementService.listByCashSession(id, principal.userId(), principal.role()).stream()
                .map(CashMovementResponse::from)
                .toList();
    }

}
