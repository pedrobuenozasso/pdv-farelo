package com.farelo.api.inventory.web;

import com.farelo.api.inventory.Recipe;
import com.farelo.api.inventory.RecipeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
 * {@code /api/v1/recipes} (FARELO-091). Header-only CRUD for {@link Recipe}
 * — no recipe items yet (FARELO-092), see {@link Recipe}'s javadoc.
 *
 * <p><b>Deactivate via {@code PATCH}, not {@code PUT}</b>: unlike {@code
 * Ingredient}/{@code Product} (which use full-replace {@code PUT} because
 * they have several independently editable fields), a {@code Recipe}
 * header has exactly one thing this ticket allows changing —
 * {@code active} — so a partial-update {@code PATCH} that only takes that
 * field fits better than a full-replace endpoint that would otherwise just
 * be an alias for the same single field. Reassigning a recipe to a
 * different product isn't supported (not requested by this ticket; the
 * right move to change composition is to deactivate and create a new
 * recipe — see {@link Recipe}'s javadoc on the {@code @ManyToOne}
 * decision).
 */
@RestController
@RequestMapping("/api/v1/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @PostMapping
    public ResponseEntity<RecipeResponse> create(
            @Valid @RequestBody RecipeRequest request,
            UriComponentsBuilder uriComponentsBuilder) {
        Recipe recipe = recipeService.create(request.productId());

        URI location = uriComponentsBuilder
                .path("/api/v1/recipes/{id}")
                .buildAndExpand(recipe.getId())
                .toUri();

        return ResponseEntity.created(location).body(RecipeResponse.from(recipe));
    }

    @GetMapping
    public List<RecipeResponse> list() {
        return recipeService.listAll().stream()
                .map(RecipeResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public RecipeResponse getById(@PathVariable UUID id) {
        return RecipeResponse.from(recipeService.getById(id));
    }

    @PatchMapping("/{id}/deactivate")
    public RecipeResponse deactivate(@PathVariable UUID id) {
        return RecipeResponse.from(recipeService.deactivate(id));
    }

}
