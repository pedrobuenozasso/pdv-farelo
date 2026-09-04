package com.farelo.api.fiscal.web;

import com.farelo.api.fiscal.FiscalProfile;
import com.farelo.api.fiscal.FiscalProfileService;
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
 * <p><b>No {@code @RequireRole} anywhere on this controller</b> — same
 * precedent as {@code IngredientController} (still entirely unprotected as
 * of this ticket, despite also being an Admin-configured lookup value) and
 * {@code Payment}/{@code Notification}/{@code AuditLog}'s own first
 * tickets: applying RBAC to a brand-new domain's endpoints is consistently
 * treated in this codebase as its own separate concern, deferred to a
 * dedicated future ticket (see FARELO-123 explicitly listing {@code
 * IngredientController} as out of scope, and FARELO-127 only reaching two
 * of {@code inventory}'s endpoints much later) — never bundled into the
 * ticket that first creates the write surface, unless that ticket's own
 * text is itself about protecting something (it isn't, here). If/when
 * fiscal profile management needs to be Admin/Manager-only, that's a
 * follow-up ticket to decide, not this one.
 */
@RestController
@RequestMapping("/api/v1/fiscal-profiles")
public class FiscalProfileController {

    private final FiscalProfileService fiscalProfileService;

    public FiscalProfileController(FiscalProfileService fiscalProfileService) {
        this.fiscalProfileService = fiscalProfileService;
    }

    @PostMapping
    public ResponseEntity<FiscalProfileResponse> create(
            @Valid @RequestBody FiscalProfileRequest request,
            UriComponentsBuilder uriComponentsBuilder) {
        FiscalProfile fiscalProfile = fiscalProfileService.create(
                request.name(), request.description(), request.ncm(), request.cfop());

        URI location = uriComponentsBuilder
                .path("/api/v1/fiscal-profiles/{id}")
                .buildAndExpand(fiscalProfile.getId())
                .toUri();

        return ResponseEntity.created(location).body(FiscalProfileResponse.from(fiscalProfile));
    }

    // No active-only filter yet — YAGNI, same as CategoryController/
    // IngredientController's list(), no consumer (Admin) asking for it yet.
    @GetMapping
    public List<FiscalProfileResponse> list() {
        return fiscalProfileService.listAll().stream()
                .map(FiscalProfileResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public FiscalProfileResponse getById(@PathVariable UUID id) {
        return FiscalProfileResponse.from(fiscalProfileService.getById(id));
    }

    @PutMapping("/{id}")
    public FiscalProfileResponse update(@PathVariable UUID id, @Valid @RequestBody FiscalProfileUpdateRequest request) {
        FiscalProfile fiscalProfile = fiscalProfileService.update(
                id, request.name(), request.description(), request.active(), request.ncm(), request.cfop());
        return FiscalProfileResponse.from(fiscalProfile);
    }

}
