package com.farelo.api.inventory;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class IngredientService {

    private final IngredientRepository ingredientRepository;

    public IngredientService(IngredientRepository ingredientRepository) {
        this.ingredientRepository = ingredientRepository;
    }

    // minimumStock (FARELO-099) is optional here — null when the client
    // doesn't configure a threshold at creation time, same "no unambiguous
    // default" reasoning as Ingredient.minimumStock's own javadoc; the
    // two-argument Ingredient constructor already leaves the field null by
    // default, so this just forwards whatever the caller passed (including
    // null) via the setter rather than needing a new constructor overload.
    public Ingredient create(String name, IngredientUnit unit, BigDecimal minimumStock) {
        Ingredient ingredient = new Ingredient(name, unit);
        ingredient.setMinimumStock(minimumStock);
        return ingredientRepository.save(ingredient);
    }

    // No active-only filter yet, same as CategoryService/ProductService's
    // listAll() — YAGNI, no consumer (Admin) asking for it yet.
    public List<Ingredient> listAll() {
        return ingredientRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    // Reused by update() below.
    public Ingredient getById(UUID id) {
        return ingredientRepository.findById(id)
                .orElseThrow(() -> new IngredientNotFoundException(id));
    }

    // minimumStock (FARELO-099) can be null here on purpose — PUT is a full
    // replace, and null is how a client explicitly clears a
    // previously-configured threshold back to "not set" (see
    // Ingredient.minimumStock's javadoc), not just "field omitted".
    @Transactional
    public Ingredient update(UUID id, String name, IngredientUnit unit, boolean active, BigDecimal minimumStock) {
        Ingredient ingredient = getById(id);

        ingredient.setName(name);
        ingredient.setUnit(unit);
        ingredient.setActive(active);
        ingredient.setMinimumStock(minimumStock);

        return ingredientRepository.save(ingredient);
    }

}
