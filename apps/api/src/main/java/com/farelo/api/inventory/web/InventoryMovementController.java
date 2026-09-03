package com.farelo.api.inventory.web;

import com.farelo.api.inventory.InventoryMovementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * {@code /api/v1/ingredients/{ingredientId}/movements} (FARELO-093): read
 * access to an ingredient's stock ledger, nested under the ingredient it
 * belongs to — same nesting shape as {@code RecipeItemController} under
 * {@code /recipes/{recipeId}/items}.
 *
 * <p><b>Read-only — no {@code POST} here.</b> Nothing in this ticket
 * produces an {@code InventoryMovement} yet (see its javadoc): a generic
 * creation endpoint now would anticipate FARELO-094's design ("Criar
 * entrada manual de estoque", the ticket that actually owns "how does a
 * human record a manual movement") before that ticket exists. This endpoint
 * only lets the ledger be inspected once rows exist (today: only via tests
 * calling the repository directly).
 */
@RestController
@RequestMapping("/api/v1/ingredients/{ingredientId}/movements")
public class InventoryMovementController {

    private final InventoryMovementService inventoryMovementService;

    public InventoryMovementController(InventoryMovementService inventoryMovementService) {
        this.inventoryMovementService = inventoryMovementService;
    }

    @GetMapping
    public List<InventoryMovementResponse> list(@PathVariable UUID ingredientId) {
        return inventoryMovementService.listByIngredient(ingredientId).stream()
                .map(InventoryMovementResponse::from)
                .toList();
    }

}
