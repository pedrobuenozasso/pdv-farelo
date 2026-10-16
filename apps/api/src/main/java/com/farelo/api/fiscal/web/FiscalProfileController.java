package com.farelo.api.fiscal.web;

import com.farelo.api.fiscal.FiscalProfile;
import com.farelo.api.fiscal.FiscalProfileService;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.rbac.RequireRole;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * {@code /api/v1/fiscal-profiles} — first endpoints of the {@code fiscal}
 * domain (FARELO-150). {@code POST}/{@code GET}/{@code GET {id}}/{@code
 * PUT}, the same full CRUD shape {@code IngredientController} shipped in
 * its own single first ticket (FARELO-090) — chosen over the narrower
 * {@code Category} precedent (whose {@code POST}/{@code GET} pair was
 * split across two separate later-numbered tickets, FARELO-012/013)
 * because, like {@code Ingredient}, there is no separate roadmap ticket for
 * "criar endpoint" here: FARELO-150 is the only ticket that stands this
 * domain up, and FARELO-151 ("Associar Product com FiscalProfile") already
 * assumes an Admin can create/list/fix a fiscal profile by the time it
 * lands. {@code PUT} in particular matters here more than it might for a
 * pure ledger-style entity: a fiscal profile is a hand-typed lookup value
 * (name/description) an Admin will want to correct a typo in before
 * FARELO-151 starts referencing it by id.
 *
 * <p>Fiscal classification changes require {@code ADMIN}; reads are
 * available to {@code ADMIN} and {@code MANAGER}.
 */
@RestController
@RequestMapping("/api/v1/fiscal-profiles")
public class FiscalProfileController {

    private final FiscalProfileService fiscalProfileService;

    public FiscalProfileController(FiscalProfileService fiscalProfileService) {
        this.fiscalProfileService = fiscalProfileService;
    }

    @PostMapping
    @RequireRole(UserRole.ADMIN)
    public ResponseEntity<FiscalProfileResponse> create(
            @Valid @RequestBody FiscalProfileRequest request,
            UriComponentsBuilder uriComponentsBuilder) {
        FiscalProfile fiscalProfile = fiscalProfileService.create(
                request.name(), request.description(), request.ncm(), request.cfop(), request.cst(), request.csosn());

        URI location = uriComponentsBuilder
                .path("/api/v1/fiscal-profiles/{id}")
                .buildAndExpand(fiscalProfile.getId())
                .toUri();

        return ResponseEntity.created(location).body(FiscalProfileResponse.from(fiscalProfile));
    }

    // No active-only filter yet — YAGNI, same as CategoryController/
    // IngredientController's list(), no consumer (Admin) asking for it yet.
    @GetMapping
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER})
    public List<FiscalProfileResponse> list() {
        return fiscalProfileService.listAll().stream()
                .map(FiscalProfileResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER})
    public FiscalProfileResponse getById(@PathVariable UUID id) {
        return FiscalProfileResponse.from(fiscalProfileService.getById(id));
    }

    @PutMapping("/{id}")
    @RequireRole(UserRole.ADMIN)
    public FiscalProfileResponse update(@PathVariable UUID id, @Valid @RequestBody FiscalProfileUpdateRequest request) {
        FiscalProfile fiscalProfile = fiscalProfileService.update(
                id, request.name(), request.description(), request.active(), request.ncm(), request.cfop(),
                request.cst(), request.csosn());
        return FiscalProfileResponse.from(fiscalProfile);
    }

}
