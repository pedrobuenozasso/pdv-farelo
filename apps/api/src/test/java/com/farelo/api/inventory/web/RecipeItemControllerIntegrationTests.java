package com.farelo.api.inventory.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import com.farelo.api.inventory.Ingredient;
import com.farelo.api.inventory.IngredientRepository;
import com.farelo.api.inventory.IngredientUnit;
import com.farelo.api.inventory.Recipe;
import com.farelo.api.inventory.RecipeItem;
import com.farelo.api.inventory.RecipeItemRepository;
import com.farelo.api.inventory.RecipeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code POST}/{@code GET}/{@code DELETE
 * /api/v1/recipes/{recipeId}/items}, against a real PostgreSQL instance
 * (Testcontainers).
 *
 * <p>No {@code @BeforeEach} table cleanup — same reasoning as {@code
 * RecipeItemRepositoryIntegrationTests}: every test creates its own fresh
 * product/category/ingredient/recipe and only asserts on data scoped to
 * that specific recipe, so leftover rows from other test classes never
 * affect an assertion here.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RecipeItemControllerIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

    @Autowired
    private ObjectMapper objectMapper;

    private Recipe createRecipe(String productName) {
        Category category = categoryRepository.save(new Category("Padaria"));
        Product product = productRepository.save(new Product(productName, new BigDecimal("12.00"), category));
        return recipeRepository.save(new Recipe(product));
    }

    private Ingredient createIngredient(String name, IngredientUnit unit) {
        return ingredientRepository.save(new Ingredient(name, unit));
    }

    @Test
    void createsRecipeItemAndPersistsIt() throws Exception {
        Recipe recipe = createRecipe("Pão com ovos e bacon");
        Ingredient bacon = createIngredient("Bacon", IngredientUnit.GRAM);

        MvcResult result = mockMvc.perform(post("/api/v1/recipes/{recipeId}/items", recipe.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ingredientId": "%s", "quantity": 80}
                                """.formatted(bacon.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.recipeId").value(recipe.getId().toString()))
                .andExpect(jsonPath("$.ingredientId").value(bacon.getId().toString()))
                .andExpect(jsonPath("$.ingredientName").value("Bacon"))
                .andExpect(jsonPath("$.ingredientUnit").value("GRAM"))
                .andExpect(jsonPath("$.quantity").value(80))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn();

        RecipeItemResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), RecipeItemResponse.class);

        Optional<RecipeItem> persisted = recipeItemRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getRecipe().getId()).isEqualTo(recipe.getId());
        assertThat(persisted.get().getIngredient().getId()).isEqualTo(bacon.getId());
        assertThat(persisted.get().getQuantity()).isEqualByComparingTo("80");
    }

    @Test
    void returnsRecipeNotFoundWhenCreatingItemForUnknownRecipe() throws Exception {
        Ingredient milk = createIngredient("Leite", IngredientUnit.MILLILITER);
        UUID missingRecipeId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/recipes/{recipeId}/items", missingRecipeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ingredientId": "%s", "quantity": 100}
                                """.formatted(milk.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECIPE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsIngredientNotFoundWhenCreatingItemWithUnknownIngredient() throws Exception {
        Recipe recipe = createRecipe("Croissant");
        UUID missingIngredientId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/recipes/{recipeId}/items", recipe.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ingredientId": "%s", "quantity": 1}
                                """.formatted(missingIngredientId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsConflictWhenIngredientAlreadyOnRecipe() throws Exception {
        Recipe recipe = createRecipe("Pão francês");
        Ingredient flour = createIngredient("Farinha de trigo", IngredientUnit.GRAM);
        recipeItemRepository.save(new RecipeItem(recipe, flour, new BigDecimal("500")));

        mockMvc.perform(post("/api/v1/recipes/{recipeId}/items", recipe.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ingredientId": "%s", "quantity": 250}
                                """.formatted(flour.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECIPE_ITEM_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsMissingIngredientIdWithStandardErrorFormat() throws Exception {
        Recipe recipe = createRecipe("Bolo de chocolate");

        mockMvc.perform(post("/api/v1/recipes/{recipeId}/items", recipe.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsNonPositiveQuantityWithStandardErrorFormat() throws Exception {
        Recipe recipe = createRecipe("Suco de laranja");
        Ingredient orange = createIngredient("Laranja", IngredientUnit.UNIT);

        mockMvc.perform(post("/api/v1/recipes/{recipeId}/items", recipe.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ingredientId": "%s", "quantity": 0}
                                """.formatted(orange.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsEmptyListWhenRecipeHasNoItems() throws Exception {
        Recipe recipe = createRecipe("Torta de limão");

        mockMvc.perform(get("/api/v1/recipes/{recipeId}/items", recipe.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void returnsRecipeNotFoundWhenListingItemsOfUnknownRecipe() throws Exception {
        UUID missingRecipeId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/recipes/{recipeId}/items", missingRecipeId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECIPE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void listsOnlyItemsOfTheGivenRecipe() throws Exception {
        Recipe recipeA = createRecipe("Empada de frango");
        Recipe recipeB = createRecipe("Empada de palmito");
        Ingredient chicken = createIngredient("Frango desfiado", IngredientUnit.GRAM);
        Ingredient heart = createIngredient("Palmito", IngredientUnit.GRAM);
        recipeItemRepository.save(new RecipeItem(recipeA, chicken, new BigDecimal("120")));
        recipeItemRepository.save(new RecipeItem(recipeB, heart, new BigDecimal("100")));

        MvcResult result = mockMvc.perform(get("/api/v1/recipes/{recipeId}/items", recipeA.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andReturn();

        List<RecipeItemResponse> items = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, RecipeItemResponse.class));

        assertThat(items).extracting(RecipeItemResponse::ingredientName).containsExactly("Frango desfiado");
    }

    @Test
    void deletesRecipeItem() throws Exception {
        Recipe recipe = createRecipe("Chá gelado");
        Ingredient ice = createIngredient("Gelo", IngredientUnit.GRAM);
        RecipeItem item = recipeItemRepository.save(new RecipeItem(recipe, ice, new BigDecimal("30")));

        mockMvc.perform(delete("/api/v1/recipes/{recipeId}/items/{itemId}", recipe.getId(), item.getId()))
                .andExpect(status().isNoContent());

        assertThat(recipeItemRepository.findById(item.getId())).isEmpty();
    }

    @Test
    void returnsRecipeNotFoundWhenDeletingItemOfUnknownRecipe() throws Exception {
        UUID missingRecipeId = UUID.randomUUID();
        UUID someItemId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/recipes/{recipeId}/items/{itemId}", missingRecipeId, someItemId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECIPE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsRecipeItemNotFoundWhenDeletingUnknownItem() throws Exception {
        Recipe recipe = createRecipe("Cappuccino");
        UUID missingItemId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/recipes/{recipeId}/items/{itemId}", recipe.getId(), missingItemId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECIPE_ITEM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsRecipeItemNotFoundWhenDeletingItemThroughAnUnrelatedRecipe() throws Exception {
        Recipe recipeA = createRecipe("Café com leite");
        Recipe recipeB = createRecipe("Café expresso");
        Ingredient milk = createIngredient("Leite", IngredientUnit.MILLILITER);
        RecipeItem item = recipeItemRepository.save(new RecipeItem(recipeA, milk, new BigDecimal("150")));

        mockMvc.perform(delete("/api/v1/recipes/{recipeId}/items/{itemId}", recipeB.getId(), item.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECIPE_ITEM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());

        assertThat(recipeItemRepository.findById(item.getId())).isPresent();
    }

}
