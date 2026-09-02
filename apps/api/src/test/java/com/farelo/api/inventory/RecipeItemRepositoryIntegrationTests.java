package com.farelo.api.inventory;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that {@link RecipeItem} maps correctly onto the table created by
 * {@code V19__create_recipe_item_table.sql}, against a real PostgreSQL
 * instance — including the {@code UNIQUE(recipe_id, ingredient_id)}
 * constraint enforcing "an ingredient appears at most once per recipe" (see
 * {@link RecipeItemAlreadyExistsException}'s javadoc).
 *
 * <p>No {@code @BeforeEach} table cleanup, same reasoning as {@code
 * IngredientRepositoryIntegrationTests}: every test creates its own fresh
 * product/category/ingredient/recipe and only ever queries/asserts scoped to
 * that specific recipe/ingredient, so leftover rows from other test classes
 * sharing the singleton Postgres container (see {@link
 * AbstractIntegrationTest}) never affect an assertion here — no need to
 * touch the shared {@code product}/{@code category} tables at all (unsafe
 * under the full suite, see {@code RecipeRepositoryIntegrationTests}'s note)
 * or even this ticket's own {@code recipe}/{@code ingredient} tables.
 */
@SpringBootTest
class RecipeItemRepositoryIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private RecipeItemRepository recipeItemRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Recipe createRecipe(String productName) {
        Category category = categoryRepository.save(new Category("Padaria"));
        Product product = productRepository.save(new Product(productName, new BigDecimal("12.00"), category));
        return recipeRepository.save(new Recipe(product));
    }

    private Ingredient createIngredient(String name, IngredientUnit unit) {
        return ingredientRepository.save(new Ingredient(name, unit));
    }

    @Test
    void savesAndFindsRecipeItem() {
        Recipe recipe = createRecipe("Pão com ovos e bacon");
        Ingredient bacon = createIngredient("Bacon", IngredientUnit.GRAM);

        RecipeItem saved = recipeItemRepository.saveAndFlush(new RecipeItem(recipe, bacon, new BigDecimal("80")));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getRecipe().getId()).isEqualTo(recipe.getId());
        assertThat(saved.getIngredient().getId()).isEqualTo(bacon.getId());
        assertThat(saved.getQuantity()).isEqualByComparingTo("80");

        Optional<RecipeItem> found = recipeItemRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getQuantity()).isEqualByComparingTo("80.000");
    }

    @Test
    void findsItemsByRecipeIdWithIngredientEagerlyFetched() {
        Recipe recipe = createRecipe("Pão de queijo");
        Ingredient cheese = createIngredient("Queijo ralado", IngredientUnit.GRAM);
        recipeItemRepository.saveAndFlush(new RecipeItem(recipe, cheese, new BigDecimal("50")));

        List<RecipeItem> items = recipeItemRepository.findByRecipeId(recipe.getId());

        assertThat(items).hasSize(1);
        // Reading ingredient fields here (outside any open Hibernate session,
        // since the repository call's transaction already closed) is exactly
        // what would throw LazyInitializationException without the JOIN
        // FETCH — see findByRecipeId's javadoc.
        assertThat(items.get(0).getIngredient().getName()).isEqualTo("Queijo ralado");
        assertThat(items.get(0).getIngredient().getUnit()).isEqualTo(IngredientUnit.GRAM);
    }

    @Test
    void findByRecipeIdOnlyReturnsItemsOfThatRecipe() {
        Recipe recipeA = createRecipe("Croissant");
        Recipe recipeB = createRecipe("Suco de laranja");
        Ingredient butter = createIngredient("Manteiga", IngredientUnit.GRAM);
        Ingredient orange = createIngredient("Laranja", IngredientUnit.UNIT);
        recipeItemRepository.saveAndFlush(new RecipeItem(recipeA, butter, new BigDecimal("10")));
        recipeItemRepository.saveAndFlush(new RecipeItem(recipeB, orange, new BigDecimal("3")));

        List<RecipeItem> items = recipeItemRepository.findByRecipeId(recipeA.getId());

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getIngredient().getId()).isEqualTo(butter.getId());
    }

    @Test
    void existsByRecipeIdAndIngredientIdReflectsPersistedItems() {
        Recipe recipe = createRecipe("Bolo de cenoura");
        Ingredient egg = createIngredient("Ovo", IngredientUnit.UNIT);

        assertThat(recipeItemRepository.existsByRecipeIdAndIngredientId(recipe.getId(), egg.getId())).isFalse();

        recipeItemRepository.saveAndFlush(new RecipeItem(recipe, egg, new BigDecimal("3")));

        assertThat(recipeItemRepository.existsByRecipeIdAndIngredientId(recipe.getId(), egg.getId())).isTrue();
    }

    @Test
    void findByIdAndRecipeIdDoesNotMatchAnotherRecipe() {
        Recipe recipeA = createRecipe("Torta de limão");
        Recipe recipeB = createRecipe("Empada de frango");
        Ingredient lemon = createIngredient("Limão", IngredientUnit.UNIT);
        RecipeItem item = recipeItemRepository.saveAndFlush(new RecipeItem(recipeA, lemon, new BigDecimal("2")));

        assertThat(recipeItemRepository.findByIdAndRecipeId(item.getId(), recipeA.getId())).isPresent();
        assertThat(recipeItemRepository.findByIdAndRecipeId(item.getId(), recipeB.getId())).isEmpty();
    }

    @Test
    void rejectsDuplicateIngredientOnTheSameRecipeAtDbLevel() {
        Recipe recipe = createRecipe("Pão francês");
        Ingredient flour = createIngredient("Farinha de trigo", IngredientUnit.GRAM);
        recipeItemRepository.saveAndFlush(new RecipeItem(recipe, flour, new BigDecimal("500")));

        assertThatThrownBy(() ->
                recipeItemRepository.saveAndFlush(new RecipeItem(recipe, flour, new BigDecimal("250"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsTheSameIngredientOnDifferentRecipes() {
        Recipe recipeA = createRecipe("Cappuccino");
        Recipe recipeB = createRecipe("Café com leite");
        Ingredient milk = createIngredient("Leite", IngredientUnit.MILLILITER);

        RecipeItem itemA = recipeItemRepository.saveAndFlush(new RecipeItem(recipeA, milk, new BigDecimal("150")));
        RecipeItem itemB = recipeItemRepository.saveAndFlush(new RecipeItem(recipeB, milk, new BigDecimal("200")));

        assertThat(itemA.getId()).isNotEqualTo(itemB.getId());
    }

    @Test
    void deletesRecipeItem() {
        Recipe recipe = createRecipe("Chá gelado");
        Ingredient ice = createIngredient("Gelo", IngredientUnit.GRAM);
        RecipeItem item = recipeItemRepository.saveAndFlush(new RecipeItem(recipe, ice, new BigDecimal("30")));
        UUID itemId = item.getId();

        recipeItemRepository.delete(item);
        recipeItemRepository.flush();

        assertThat(recipeItemRepository.findById(itemId)).isEmpty();
    }

}
