package com.farelo.api.inventory;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class IngredientService {

    private final IngredientRepository ingredientRepository;

    public IngredientService(IngredientRepository ingredientRepository) {
        this.ingredientRepository = ingredientRepository;
    }

    public Ingredient create(String name, IngredientUnit unit) {
        Ingredient ingredient = new Ingredient(name, unit);
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

    @Transactional
    public Ingredient update(UUID id, String name, IngredientUnit unit, boolean active) {
        Ingredient ingredient = getById(id);

        ingredient.setName(name);
        ingredient.setUnit(unit);
        ingredient.setActive(active);

        return ingredientRepository.save(ingredient);
    }

}
