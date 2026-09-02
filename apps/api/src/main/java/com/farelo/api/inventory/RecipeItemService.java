package com.farelo.api.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class RecipeItemService {

    private final RecipeItemRepository recipeItemRepository;
    private final RecipeService recipeService;
    private final IngredientService ingredientService;

    public RecipeItemService(
            RecipeItemRepository recipeItemRepository,
            RecipeService recipeService,
            IngredientService ingredientService) {
        this.recipeItemRepository = recipeItemRepository;
        this.recipeService = recipeService;
        this.ingredientService = ingredientService;
    }

    /**
     * Adds an item to a recipe. Validates the recipe exists (reuses
     * {@link RecipeService#getById(UUID)} — 404 {@link RecipeNotFoundException}
     * if it doesn't) and the ingredient exists (reuses
     * {@link IngredientService#getById(UUID)} — 404
     * {@link IngredientNotFoundException} if it doesn't), same cross-domain
     * reuse pattern already used by {@link RecipeService#create(UUID)} for
     * {@code ProductService}. Then rejects a duplicate ingredient on the same
     * recipe (409 {@link RecipeItemAlreadyExistsException} — see its javadoc
     * for why this is checked here *and* backed by a
     * {@code UNIQUE(recipe_id, ingredient_id)} constraint at the DB level).
     */
    @Transactional
    public RecipeItem create(UUID recipeId, UUID ingredientId, BigDecimal quantity) {
        Recipe recipe = recipeService.getById(recipeId);
        Ingredient ingredient = ingredientService.getById(ingredientId);

        if (recipeItemRepository.existsByRecipeIdAndIngredientId(recipeId, ingredientId)) {
            throw new RecipeItemAlreadyExistsException(recipeId, ingredientId);
        }

        return recipeItemRepository.save(new RecipeItem(recipe, ingredient, quantity));
    }

    // Validates the recipe exists first (404 RECIPE_NOT_FOUND) so that
    // listing items for an unknown recipe id is distinguishable from a real
    // recipe that simply has no items yet (both would otherwise return an
    // identical empty list). Uses the JOIN FETCH query — see
    // RecipeItemRepository#findByRecipeId.
    public List<RecipeItem> listByRecipe(UUID recipeId) {
        recipeService.getById(recipeId);
        return recipeItemRepository.findByRecipeId(recipeId);
    }

    // Deletes one item from a recipe (see RecipeItemController's DELETE
    // endpoint). Physical delete, not a soft "active" flag — see
    // RecipeItemController's javadoc for why that departs from Recipe's
    // deactivate-only pattern and is still the right call here.
    @Transactional
    public void delete(UUID recipeId, UUID itemId) {
        recipeService.getById(recipeId);

        RecipeItem item = recipeItemRepository.findByIdAndRecipeId(itemId, recipeId)
                .orElseThrow(() -> new RecipeItemNotFoundException(itemId));

        recipeItemRepository.delete(item);
    }

}
