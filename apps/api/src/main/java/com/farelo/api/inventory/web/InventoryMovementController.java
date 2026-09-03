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
 * {@code /api/v1/ingredients/{ingredientId}/movements} (FARELO-093): read
 * access to an ingredient's stock ledger, nested under the ingredient it
 * belongs to — same nesting shape as {@code RecipeItemController} under
 * {@code /recipes/{recipeId}/items}.
 *
 * <p><b>{@code POST} — manual stock entry (FARELO-094).</b> Originally this
 * controller was read-only (see git history / {@code docs/domain-model.md}
 * for the FARELO-093 reasoning): a generic creation endpoint at the time
 * would have anticipated FARELO-094's design before that ticket existed.
 * This ticket *is* FARELO-094 — it adds exactly one producer, the manual
 * entry flow (a human recording that stock physically arrived), always
 * creating a {@code PURCHASE} row. It is deliberately not a generic
 * "create any movement type" endpoint — see {@link
 * InventoryMovementRequest}'s javadoc.
 */
@RestController
@RequestMapping("/api/v1/ingredients/{ingredientId}/movements")
public class InventoryMovementController {

    private final InventoryMovementService inventoryMovementService;

    public InventoryMovementController(InventoryMovementService inventoryMovementService) {
        this.inventoryMovementService = inventoryMovementService;
    }

    @PostMapping
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

    @GetMapping
    public List<InventoryMovementResponse> list(@PathVariable UUID ingredientId) {
        return inventoryMovementService.listByIngredient(ingredientId).stream()
                .map(InventoryMovementResponse::from)
                .toList();
    }

}
