package com.farelo.api.cashier.web;

import com.farelo.api.cashier.Shift;
import com.farelo.api.cashier.ShiftService;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.rbac.RequireRole;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * {@code /api/v1/shifts} (FARELO-306). First controller of the new {@code
 * cashier} domain.
 *
 * <p><b>Both endpoints require a role, unlike {@code
 * CategoryController#list}/{@code PaymentController#listByCommand}'s
 * unprotected-first-read-endpoint precedent</b>: those two stay open
 * because a real, unauthenticated consumer needs them (the public "Cardápio
 * QR" menu; a comanda's own payment ledger, read by PDV staff with no
 * stricter need than "logged in as staff at all" at the time). {@code
 * Shift} has no such consumer — it exists purely to be picked from when
 * opening a {@code CashSession} (FARELO-307), an inherently staff-only,
 * cash-handling-adjacent action, so there is no reason to leave either
 * endpoint open. {@link #create} requires {@code ADMIN}/{@code MANAGER}
 * (config-authoring, same role list {@code CategoryController#create}
 * already uses for menu-category authoring — an analogous "back-office sets
 * up a reusable template" action). {@link #list} additionally allows {@code
 * CASHIER}: a cashier opening a cash session needs to see which templates
 * exist to pick one, the same reason {@code CashSessionController#open}
 * (FARELO-307) itself allows {@code CASHIER}.
 */
@RestController
@RequestMapping("/api/v1/shifts")
public class ShiftController {

    private final ShiftService shiftService;

    public ShiftController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }

    @PostMapping
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER})
    public ResponseEntity<ShiftResponse> create(
            @Valid @RequestBody ShiftRequest request,
            UriComponentsBuilder uriComponentsBuilder) {
        Shift shift = shiftService.create(request.name(), request.openingAccountName(), request.closingAccountName());

        URI location = uriComponentsBuilder
                .path("/api/v1/shifts/{id}")
                .buildAndExpand(shift.getId())
                .toUri();

        return ResponseEntity.created(location).body(ShiftResponse.from(shift));
    }

    @GetMapping
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER})
    public List<ShiftResponse> list() {
        return shiftService.listAll().stream()
                .map(ShiftResponse::from)
                .toList();
    }

}
