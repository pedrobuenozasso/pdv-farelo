package com.farelo.api.inventory.web;

import com.farelo.api.inventory.Ingredient;
import com.farelo.api.inventory.IngredientService;
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

@RestController
@RequestMapping("/api/v1/ingredients")
public class IngredientController {

    private final IngredientService ingredientService;

    public IngredientController(IngredientService ingredientService) {
        this.ingredientService = ingredientService;
    }

    @PostMapping
    public ResponseEntity<IngredientResponse> create(
            @Valid @RequestBody IngredientRequest request,
            UriComponentsBuilder uriComponentsBuilder) {
        Ingredient ingredient = ingredientService.create(request.name(), request.unit(), request.minimumStock());

        URI location = uriComponentsBuilder
                .path("/api/v1/ingredients/{id}")
                .buildAndExpand(ingredient.getId())
                .toUri();

        return ResponseEntity.created(location).body(IngredientResponse.from(ingredient));
    }

    // No active-only filter yet — YAGNI, same as CategoryController/
    // ProductController's list(), no consumer (Admin) asking for it yet.
    @GetMapping
    public List<IngredientResponse> list() {
        return ingredientService.listAll().stream()
                .map(IngredientResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public IngredientResponse getById(@PathVariable UUID id) {
        return IngredientResponse.from(ingredientService.getById(id));
    }

    @PutMapping("/{id}")
    public IngredientResponse update(@PathVariable UUID id, @Valid @RequestBody IngredientUpdateRequest request) {
        Ingredient ingredient = ingredientService.update(
                id, request.name(), request.unit(), request.active(), request.minimumStock());
        return IngredientResponse.from(ingredient);
    }

}
