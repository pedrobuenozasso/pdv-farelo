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
 * (a read over the same operational data, no stricter need identified
 * yet).
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
        CashSession session = cashSessionService.open(request.shiftId(), principal.userId());

        URI location = uriComponentsBuilder
                .path("/api/v1/cash-sessions/{id}")
                .buildAndExpand(session.getId())
                .toUri();

        return ResponseEntity.created(location).body(CashSessionResponse.from(session));
    }

    @GetMapping
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER})
    public List<CashSessionResponse> list() {
        return cashSessionService.listAll().stream()
                .map(CashSessionResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER})
    public CashSessionResponse getById(@PathVariable UUID id) {
        return CashSessionResponse.from(cashSessionService.getById(id));
    }

}
