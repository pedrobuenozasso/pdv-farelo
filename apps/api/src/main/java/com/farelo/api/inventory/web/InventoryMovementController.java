package com.farelo.api.inventory.web;

import com.farelo.api.inventory.InventoryMovement;
import com.farelo.api.inventory.InventoryMovementService;
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
 * {@code /api/v1/ingredients/{ingredientId}/...} (FARELO-093): everything
 * about an ingredient's stock ledger — reading it, adding to it, and (as of
 * FARELO-095) computing its derived balance — nested under the ingredient it
 * belongs to, same nesting shape as {@code RecipeItemController} under
 * {@code /recipes/{recipeId}/items}.
 *
 * <p><b>{@code POST}/{@code GET .../movements} — manual stock entry
 * (FARELO-094) and ledger listing (FARELO-093).</b> Originally this
 * controller was read-only (see git history / {@code docs/domain-model.md}
 * for the FARELO-093 reasoning): a generic creation endpoint at the time
 * would have anticipated FARELO-094's design before that ticket existed.
 * FARELO-094 added exactly one producer, the manual entry flow (a human
 * recording that stock physically arrived), always creating a {@code
 * PURCHASE} row. It is deliberately not a generic "create any movement
 * type" endpoint — see {@link InventoryMovementRequest}'s javadoc.
 *
 * <p><b>FARELO-127</b>: {@link #create} and {@link #recordLoss} now require
 * {@link UserRole#ADMIN}/{@link UserRole#MANAGER} and declare an {@link
 * AuthenticatedPrincipal} parameter — the first {@code @RequireRole} usage
 * anywhere in this controller (or in {@code IngredientController}/{@code
 * RecipeController}, which remain completely untouched). This is a
 * narrow, deliberate exception to {@code RequireRole}'s own javadoc, which
 * had listed the whole {@code inventory} write surface as "out of scope for
 * both FARELO-123 and FARELO-124... a distinct future ticket": auditing a
 * stock adjustment (this ticket) requires knowing who performed it, and
 * there is no reliable "who" without a real, server-verified caller
 * identity — see {@code InventoryMovementService#recordAudit}'s javadoc for
 * the full three-option writeup (why a client-supplied actor id was
 * rejected, and why this is scoped to exactly these two endpoints, not the
 * whole controller or the sibling {@code Ingredient}/{@code Recipe}
 * controllers). {@link #list}/{@link #getBalance} below stay deliberately
 * <b>unannotated</b> — read access to the ledger/balance carries none of
 * this ticket's actor-attribution requirement and was never in scope.
 *
 * <p><b>{@code GET .../balance} (FARELO-095)</b>: class-level {@code
 * @RequestMapping} was widened from {@code .../movements} to just {@code
 * /api/v1/ingredients/{ingredientId}} (with {@code /movements} pushed onto
 * the two existing handlers above) specifically to make room for this
 * sibling route, {@code /api/v1/ingredients/{ingredientId}/balance} — a
 * balance is not one more shape of ledger entry, it's a value *derived from*
 * the ledger, so it doesn't belong nested under {@code .../movements}
 * itself. This stays in {@code InventoryMovementController} rather than
 * moving to {@code IngredientController} because the computation itself
 * ({@link InventoryMovementService#getBalance}) lives in this same service
 * this controller already talks to exclusively; putting the endpoint on
 * {@code IngredientController} instead would make it the first controller in
 * this codebase to depend on two services, for no benefit — same
 * "keep the dependency one-directional" reasoning {@code
 * CommandOrdersController}'s javadoc already documents for a similar
 * choice, just within one package instead of across two.
 *
 * <p><b>{@code POST .../losses} (FARELO-098, "Criar movimento de
 * perda")</b>: a sibling route to {@code .../movements}/{@code .../balance}
 * above, nested the same way, for the second manual producer of {@link
 * InventoryMovement} rows — a human recording that stock was lost
 * (spoilage/breakage/theft, not a sale). Deliberately its own endpoint
 * rather than a {@code type} field on {@code POST .../movements}: same
 * "don't let a client pick the type through an endpoint that has nothing to
 * do with that flow" reasoning {@link InventoryMovementRequest}'s javadoc
 * already documents for why that endpoint is PURCHASE-only. See {@link
 * InventoryLossRequest}'s javadoc for why its {@code quantity} field is a
 * positive magnitude and {@link
 * com.farelo.api.inventory.InventoryMovementService#recordLoss} for where
 * the sign actually gets negated.
 */
@RestController
@RequestMapping("/api/v1/ingredients/{ingredientId}")
public class InventoryMovementController {

    private final InventoryMovementService inventoryMovementService;

    public InventoryMovementController(InventoryMovementService inventoryMovementService) {
        this.inventoryMovementService = inventoryMovementService;
    }

    // AuthenticatedPrincipal (FARELO-127): this method is @RequireRole-
    // protected, so RoleAuthorizationInterceptor always populates one before
    // this handler runs (see AuthenticatedPrincipalArgumentResolver's
    // javadoc). Only principal.userId() is forwarded — InventoryMovementService
    // resolves it to a real User and records the audit entry itself; see
    // InventoryMovementService#recordAudit's javadoc for why that decision
    // lives there, not here.
    @PostMapping("/movements")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER})
    public ResponseEntity<InventoryMovementResponse> create(
            @PathVariable UUID ingredientId,
            @Valid @RequestBody InventoryMovementRequest request,
            UriComponentsBuilder uriComponentsBuilder,
            AuthenticatedPrincipal principal) {
        InventoryMovement movement = inventoryMovementService.create(
                ingredientId, request.quantity(), principal.userId());

        URI location = uriComponentsBuilder
                .path("/api/v1/ingredients/{ingredientId}/movements/{id}")
                .buildAndExpand(ingredientId, movement.getId())
                .toUri();

        return ResponseEntity.created(location).body(InventoryMovementResponse.from(movement));
    }

    @GetMapping("/movements")
    public List<InventoryMovementResponse> list(@PathVariable UUID ingredientId) {
        return inventoryMovementService.listByIngredient(ingredientId).stream()
                .map(InventoryMovementResponse::from)
                .toList();
    }

    // Validates the ingredient exists first (404 INGREDIENT_NOT_FOUND) —
    // enforced inside InventoryMovementService#getBalance, same order as
    // create()/list() above — before the balance is ever computed.
    @GetMapping("/balance")
    public IngredientBalanceResponse getBalance(@PathVariable UUID ingredientId) {
        return IngredientBalanceResponse.from(inventoryMovementService.getBalance(ingredientId));
    }

    // FARELO-098 — see this class's javadoc ("POST .../losses" section) for
    // why this is its own endpoint rather than a type field on create()
    // above. Location points at .../movements/{id}, not .../losses/{id} —
    // same reasoning as create(): this resource has no single-item GET of
    // its own, it's only recoverable via the ledger listing endpoint above
    // (which lists every type, LOSS included), so that's the URL that
    // actually resolves.
    //
    // AuthenticatedPrincipal (FARELO-127): same reasoning as create() above
    // — this method is @RequireRole-protected, so a principal is always
    // populated; only principal.userId() is forwarded.
    @PostMapping("/losses")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER})
    public ResponseEntity<InventoryMovementResponse> recordLoss(
            @PathVariable UUID ingredientId,
            @Valid @RequestBody InventoryLossRequest request,
            UriComponentsBuilder uriComponentsBuilder,
            AuthenticatedPrincipal principal) {
        InventoryMovement movement = inventoryMovementService.recordLoss(
                ingredientId, request.quantity(), principal.userId());

        URI location = uriComponentsBuilder
                .path("/api/v1/ingredients/{ingredientId}/movements/{id}")
                .buildAndExpand(ingredientId, movement.getId())
                .toUri();

        return ResponseEntity.created(location).body(InventoryMovementResponse.from(movement));
    }

}
