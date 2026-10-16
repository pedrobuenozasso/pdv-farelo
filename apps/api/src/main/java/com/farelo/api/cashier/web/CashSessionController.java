package com.farelo.api.cashier.web;

import com.farelo.api.cashier.CashSession;
import com.farelo.api.cashier.CashSessionService;
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
 * {@code /api/v1/cash-sessions} (FARELO-307: {@code POST} to open, {@code
 * GET}/{@code GET /{id}} to list/inspect). First controller against
 * {@link CashSession}.
 *
 * <p><b>Every endpoint here requires a role</b> — same reasoning as {@code
 * ShiftController}'s javadoc: nothing in this domain has a public/
 * unauthenticated consumer to leave open, unlike {@code
 * CategoryController#list}/{@code PaymentController#listByCommand}. {@link
 * #open} requires {@code ADMIN}/{@code MANAGER}/{@code CASHIER} — same
 * cash-handling role list {@code PaymentController#record}/{@code #close}
 * already use. {@link #list}/{@link #getById} allow the same three roles
 * (a read over the same operational data). Within that list, a CASHIER can
 * access only sessions opened by that user; ADMIN/MANAGER supervise all.
 * {@link #close} (FARELO-308) reuses that same role list — closing a
 * caixa is as much a cash-handling action as opening/recording a payment
 * against one.
 *
 * <p><b>{@link #reconcile} (FARELO-309) requires only {@code ADMIN}/{@code
 * MANAGER}, deliberately excluding {@code CASHIER}</b> — segregation of
 * duties: conferência is a checking step over the operator's own cash
 * handling, and letting that same operator self-certify their count would
 * defeat the point of the control. No direct precedent for this exclusion
 * elsewhere in this codebase (every other cash-handling action allows
 * {@code CASHIER}) — a deliberate, non-trivial decision of this ticket,
 * documented in full in {@code docs/domain-model.md}, section {@code
 * cashier}.
 */
@RestController
@RequestMapping("/api/v1/cash-sessions")
public class CashSessionController {

    private final CashSessionService cashSessionService;

    public CashSessionController(CashSessionService cashSessionService) {
        this.cashSessionService = cashSessionService;
    }

    @PostMapping
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER})
    public ResponseEntity<CashSessionResponse> open(
            @Valid @RequestBody CashSessionOpenRequest request,
            AuthenticatedPrincipal principal,
            UriComponentsBuilder uriComponentsBuilder) {
        CashSession session = cashSessionService.open(request.shiftId(), request.openingAmount(), principal.userId());

        URI location = uriComponentsBuilder
                .path("/api/v1/cash-sessions/{id}")
                .buildAndExpand(session.getId())
                .toUri();

        return ResponseEntity.created(location).body(CashSessionResponse.from(session));
    }

    @GetMapping
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER})
    public List<CashSessionResponse> list(AuthenticatedPrincipal principal) {
        return cashSessionService.listVisibleTo(principal.userId(), principal.role()).stream()
                .map(CashSessionResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER})
    public CashSessionResponse getById(@PathVariable UUID id, AuthenticatedPrincipal principal) {
        return CashSessionResponse.from(cashSessionService.getAccessibleById(
                id, principal.userId(), principal.role()));
    }

    // FARELO-308. Same cash-handling role list as open() above.
    @PostMapping("/{id}/close")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER})
    public CashSessionResponse close(
            @PathVariable UUID id,
            @Valid @RequestBody CashSessionCloseRequest request,
            AuthenticatedPrincipal principal) {
        CashSession session = cashSessionService.close(
                id, request.closingAmount(), principal.userId(), principal.role());
        return CashSessionResponse.from(session);
    }

    // FARELO-309. No request body — the comparison is entirely computed
    // server-side (see CashSessionService#reconcile's javadoc). Restricted
    // to ADMIN/MANAGER, deliberately excluding CASHIER — see this class's
    // javadoc for the segregation-of-duties reasoning.
    @PostMapping("/{id}/reconcile")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER})
    public CashSessionResponse reconcile(@PathVariable UUID id, AuthenticatedPrincipal principal) {
        CashSession session = cashSessionService.reconcile(id, principal.userId());
        return CashSessionResponse.from(session);
    }

}
