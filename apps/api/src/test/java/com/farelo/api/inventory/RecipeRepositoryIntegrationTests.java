package com.farelo.api.inventory;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that {@link Recipe} maps correctly onto the table created by
 * {@code V17__create_recipe_table.sql}, against a real PostgreSQL instance
 * — including the partial unique index enforcing "at most one active
 * recipe per product" (see {@link Recipe}'s javadoc).
 */
@SpringBootTest
class RecipeRepositoryIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    // Only this class's own table is wiped between tests. Deleting all
    // products/categories here (as CategoryControllerIntegrationTests/
    // ProductControllerIntegrationTests do) is NOT safe in this class: under
    // the full suite, other already-executed classes (e.g.
    // OrderControllerIntegrationTests) create their own Products/Orders and
    // never clean them up, so a blind productRepository.deleteAll() here can
    // hit a live order_item FK depending on class execution order — this
    // surfaced as a real, order-dependent failure during review. None of
    // this class's assertions depend on the product/category tables being
    // empty (each test creates its own uniquely-named product), so simply
    // not touching those shared tables sidesteps the problem entirely.
    @BeforeEach
    void cleanTables() {
        recipeRepository.deleteAll();
    }

    private Product createProduct(String name) {
        Category category = categoryRepository.save(new Category("Padaria"));
        return productRepository.save(new Product(name, new BigDecimal("12.00"), category));
    }

    @Test
    void savesAndFindsRecipe() {
        Product product = createProduct("Pão com ovos e bacon");

        Recipe saved = recipeRepository.saveAndFlush(new Recipe(product));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getProduct().getId()).isEqualTo(product.getId());
    }

    @Test
    void findsActiveRecipeByProductId() {
        Product product = createProduct("Croissant");
        recipeRepository.saveAndFlush(new Recipe(product));

        Optional<Recipe> found = recipeRepository.findByProductIdAndActiveTrue(product.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getProduct().getId()).isEqualTo(product.getId());
    }

    @Test
    void doesNotFindInactiveRecipeByProductId() {
        Product product = createProduct("Suco de laranja");
        Recipe recipe = recipeRepository.saveAndFlush(new Recipe(product));
        recipe.setActive(false);
        recipeRepository.saveAndFlush(recipe);

        Optional<Recipe> found = recipeRepository.findByProductIdAndActiveTrue(product.getId());

        assertThat(found).isEmpty();
    }

    @Test
    void rejectsTwoActiveRecipesForTheSameProductAtDbLevel() {
        Product product = createProduct("Bolo de cenoura");
        recipeRepository.saveAndFlush(new Recipe(product));

        assertThatThrownBy(() -> recipeRepository.saveAndFlush(new Recipe(product)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsASecondActiveRecipeOnceTheFirstIsDeactivated() {
        Product product = createProduct("Bolo de chocolate");
        Recipe first = recipeRepository.saveAndFlush(new Recipe(product));
        first.setActive(false);
        recipeRepository.saveAndFlush(first);

        Recipe second = recipeRepository.saveAndFlush(new Recipe(product));

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(recipeRepository.findByProductIdAndActiveTrue(product.getId()))
                .contains(second);
    }

}
