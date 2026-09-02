package com.farelo.api.inventory.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import com.farelo.api.inventory.Recipe;
import com.farelo.api.inventory.RecipeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code POST}/{@code GET}/{@code PATCH
 * /api/v1/recipes}, against a real PostgreSQL instance (Testcontainers).
 *
 * <p>Same reasoning as {@code IngredientControllerIntegrationTests}: the
 * shared singleton Postgres container (see {@link AbstractIntegrationTest})
 * means these tables may already have rows from other test classes, so
 * tests that assert list contents clear them first (recipe before product
 * before category, because of the FKs).
 */
@SpringBootTest
@AutoConfigureMockMvc
class RecipeControllerIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // See RecipeRepositoryIntegrationTests#cleanTables for why this only
    // wipes the recipe table: blindly deleting all products/categories
    // isn't safe under the full suite (other classes, e.g.
    // OrderControllerIntegrationTests, create Products/Orders and never
    // clean them up), and none of this class's assertions depend on the
    // product/category tables being empty.
    @BeforeEach
    void cleanTables() {
        recipeRepository.deleteAll();
    }

    private Product createProduct(String name) {
        Category category = categoryRepository.save(new Category("Padaria"));
        return productRepository.save(new Product(name, new BigDecimal("12.00"), category));
    }

    @Test
    void createsRecipeAndPersistsIt() throws Exception {
        Product product = createProduct("Pão com ovos e bacon");

        MvcResult result = mockMvc.perform(post("/api/v1/recipes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId": "%s"}
                                """.formatted(product.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.productId").value(product.getId().toString()))
                .andExpect(jsonPath("$.productName").value("Pão com ovos e bacon"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn();

        RecipeResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), RecipeResponse.class);

        Optional<Recipe> persisted = recipeRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getProduct().getId()).isEqualTo(product.getId());
        assertThat(persisted.get().isActive()).isTrue();
    }

    @Test
    void returnsProductNotFoundWhenCreatingRecipeForUnknownProduct() throws Exception {
        UUID missingProductId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/recipes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId": "%s"}
                                """.formatted(missingProductId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsConflictWhenProductAlreadyHasAnActiveRecipe() throws Exception {
        Product product = createProduct("Croissant");
        recipeRepository.save(new Recipe(product));

        mockMvc.perform(post("/api/v1/recipes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId": "%s"}
                                """.formatted(product.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECIPE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsMissingProductIdWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/recipes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsEmptyListWhenNoRecipesExist() throws Exception {
        mockMvc.perform(get("/api/v1/recipes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void returnsAllCreatedRecipes() throws Exception {
        Product first = createProduct("Pão de queijo");
        Product second = createProduct("Bolo de cenoura");
        recipeRepository.save(new Recipe(first));
        recipeRepository.save(new Recipe(second));

        MvcResult result = mockMvc.perform(get("/api/v1/recipes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn();

        List<RecipeResponse> recipes = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, RecipeResponse.class));

        assertThat(recipes)
                .extracting(RecipeResponse::productName)
                .containsExactlyInAnyOrder("Pão de queijo", "Bolo de cenoura");
    }

    @Test
    void findsRecipeById() throws Exception {
        Product product = createProduct("Suco de laranja");
        Recipe recipe = recipeRepository.save(new Recipe(product));

        mockMvc.perform(get("/api/v1/recipes/{id}", recipe.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(recipe.getId().toString()))
                .andExpect(jsonPath("$.productId").value(product.getId().toString()))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void returnsRecipeNotFoundWhenGettingUnknownId() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/recipes/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECIPE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void deactivatesRecipeAndPersistsChange() throws Exception {
        Product product = createProduct("Torta de limão");
        Recipe recipe = recipeRepository.save(new Recipe(product));

        mockMvc.perform(patch("/api/v1/recipes/{id}/deactivate", recipe.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(recipe.getId().toString()))
                .andExpect(jsonPath("$.active").value(false));

        Optional<Recipe> persisted = recipeRepository.findById(recipe.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().isActive()).isFalse();
    }

    @Test
    void allowsCreatingANewActiveRecipeAfterDeactivatingThePrevious() throws Exception {
        Product product = createProduct("Empada de frango");
        Recipe recipe = recipeRepository.save(new Recipe(product));

        mockMvc.perform(patch("/api/v1/recipes/{id}/deactivate", recipe.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/recipes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId": "%s"}
                                """.formatted(product.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void returnsRecipeNotFoundWhenDeactivatingUnknownRecipe() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/recipes/{id}/deactivate", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECIPE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

}
