package com.farelo.api.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipeItemRepository extends JpaRepository<RecipeItem, UUID> {

    // Backs RecipeItemService#create's duplicate-ingredient check (see
    // RecipeItemAlreadyExistsException's javadoc). Plain derived query, no
    // JOIN FETCH needed — a boolean result never touches recipe/ingredient
    // field data.
    boolean existsByRecipeIdAndIngredientId(UUID recipeId, UUID ingredientId);

    // Backs RecipeItemService#delete: confirms the item both exists and
    // belongs to the given recipe (a mismatched recipeId/itemId pair — e.g.
    // deleting item X via a different recipe's URL — is treated as 404, not
    // silently ignored or a cross-recipe delete). No JOIN FETCH needed: the
    // entity is only handed to repository.delete(...), whose fields are
    // never read by the controller/response.
    Optional<RecipeItem> findByIdAndRecipeId(UUID id, UUID recipeId);

    // Backs GET /api/v1/recipes/{recipeId}/items (RecipeItemService#listByRecipe)
    // — the query a recipe's composition listing needs. JOIN FETCH ri.ingredient,
    // same reasoning as RecipeRepository's findByIdWithProduct/
    // findAllWithProductOrderByCreatedAtAsc (the FARELO-055 lesson): open-in-view
    // is false (application.yml), and RecipeItemResponse#from reads
    // item.getIngredient().getName()/getUnit() in the controller, after this
    // method's own (short) transaction has already closed — without eagerly
    // fetching ingredient here, that's an uninitialized lazy proxy needing a
    // live session, i.e. a guaranteed LazyInitializationException. No JOIN
    // FETCH on ri.recipe: the response only reads recipe.getId() (the FK
    // value, already present on the lazy proxy without initializing it), not
    // any of its actual field data.
    @Query("SELECT ri FROM RecipeItem ri JOIN FETCH ri.ingredient WHERE ri.recipe.id = :recipeId ORDER BY ri.createdAt ASC")
    List<RecipeItem> findByRecipeId(@Param("recipeId") UUID recipeId);

}
