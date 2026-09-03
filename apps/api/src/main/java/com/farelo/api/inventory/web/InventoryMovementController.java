package com.farelo.api.inventory.web;

import com.farelo.api.inventory.InventoryMovement;
import com.farelo.api.inventory.InventoryMovementService;
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
 */
@RestController
@RequestMapping("/api/v1/ingredients/{ingredientId}")
public class InventoryMovementController {

    private final InventoryMovementService inventoryMovementService;

    public InventoryMovementController(InventoryMovementService inventoryMovementService) {
        this.inventoryMovementService = inventoryMovementService;
    }

    @PostMapping("/movements")
    public ResponseEntity<InventoryMovementResponse> create(
            @PathVariable UUID ingredientId,
            @Valid @RequestBody InventoryMovementRequest request,
            UriComponentsBuilder uriComponentsBuilder) {
        InventoryMovement movement = inventoryMovementService.create(ingredientId, request.quantity());

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

}
