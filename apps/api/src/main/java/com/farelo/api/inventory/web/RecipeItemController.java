package com.farelo.api.inventory.web;

import com.farelo.api.inventory.RecipeItem;
import com.farelo.api.inventory.RecipeItemService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * {@code /api/v1/recipes/{recipeId}/items} (FARELO-092): the ingredients and
 * quantities that make up a {@link com.farelo.api.inventory.Recipe}'s
 * composition, nested under the recipe they belong to.
 *
 * <p><b>{@code DELETE}, not a soft {@code active}/deactivate flag</b>: this
 * departs from {@code Recipe}'s own pattern (which never hard-deletes, only
 * deactivates — see {@code Recipe}'s javadoc), and it's a deliberate,
 * considered departure rather than an oversight. {@code Recipe} deactivation
 * exists to preserve a historical trail of a product's *composition over
 * time* (a real, if secondary, benefit called out in {@code Recipe}'s
 * javadoc — relevant once {@code InventoryMovement} consumption references
 * a specific recipe). A single {@code RecipeItem} carries no equivalent
 * standalone audit value: it's one line of composition, not a fact anyone
 * needs to reconstruct in isolation from the rest of the recipe. Removing an
 * ingredient from an already-active recipe is presumptively a direct
 * administrative correction (e.g. "we don't use butter in this anymore"),
 * not an operation that itself needs to preserve row-level history — the
 * same way {@code PUT /api/v1/ingredients/{id}} freely rewrites an
 * ingredient's fields in place with no soft-delete concept for the previous
 * values. If a future ticket needs to know exactly what a recipe's
 * composition was at some point in the past (beyond "this whole recipe
 * version was active/inactive"), the fix is versioning {@code Recipe}
 * itself more granularly, not preserving deleted {@code RecipeItem} rows.
 */
@RestController
@RequestMapping("/api/v1/recipes/{recipeId}/items")
public class RecipeItemController {

    private final RecipeItemService recipeItemService;

    public RecipeItemController(RecipeItemService recipeItemService) {
        this.recipeItemService = recipeItemService;
    }

    @PostMapping
    public ResponseEntity<RecipeItemResponse> create(
            @PathVariable UUID recipeId,
            @Valid @RequestBody RecipeItemRequest request,
            UriComponentsBuilder uriComponentsBuilder) {
        RecipeItem item = recipeItemService.create(recipeId, request.ingredientId(), request.quantity());

        URI location = uriComponentsBuilder
                .path("/api/v1/recipes/{recipeId}/items/{id}")
                .buildAndExpand(recipeId, item.getId())
                .toUri();

        return ResponseEntity.created(location).body(RecipeItemResponse.from(item));
    }

    @GetMapping
    public List<RecipeItemResponse> list(@PathVariable UUID recipeId) {
        return recipeItemService.listByRecipe(recipeId).stream()
                .map(RecipeItemResponse::from)
                .toList();
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> delete(@PathVariable UUID recipeId, @PathVariable UUID itemId) {
        recipeItemService.delete(recipeId, itemId);
        return ResponseEntity.noContent().build();
    }

}
