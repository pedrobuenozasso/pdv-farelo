package com.farelo.api.inventory.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.inventory.Ingredient;
import com.farelo.api.inventory.IngredientRepository;
import com.farelo.api.inventory.IngredientUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code POST}/{@code GET}/{@code PUT
 * /api/v1/ingredients}, against a real PostgreSQL instance (Testcontainers).
 *
 * <p>Same reasoning as {@code CategoryControllerIntegrationTests}/{@code
 * ProductControllerIntegrationTests}: the shared singleton Postgres
 * container (see {@link AbstractIntegrationTest}) means the {@code
 * ingredient} table may already have rows from other test classes, so tests
 * that assert list contents clear it first.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IngredientControllerIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanInventoryTables() {
        ingredientRepository.deleteAll();
    }

    @Test
    void createsIngredientAndPersistsIt() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/ingredients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Leite", "unit": "MILLILITER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Leite"))
                .andExpect(jsonPath("$.unit").value("MILLILITER"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn();

        IngredientResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), IngredientResponse.class);

        Optional<Ingredient> persisted = ingredientRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getName()).isEqualTo("Leite");
        assertThat(persisted.get().getUnit()).isEqualTo(IngredientUnit.MILLILITER);
        assertThat(persisted.get().isActive()).isTrue();
    }

    @Test
    void rejectsBlankNameWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/ingredients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "unit": "GRAM"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsMissingUnitWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/ingredients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Leite"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsInvalidUnitWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/ingredients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Leite", "unit": "LITER"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsEmptyListWhenNoIngredientsExist() throws Exception {
        mockMvc.perform(get("/api/v1/ingredients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void returnsAllCreatedIngredientsSortedByName() throws Exception {
        ingredientRepository.save(new Ingredient("Leite", IngredientUnit.MILLILITER));
        ingredientRepository.save(new Ingredient("Café em grão", IngredientUnit.GRAM));

        MvcResult result = mockMvc.perform(get("/api/v1/ingredients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn();

        List<IngredientResponse> ingredients = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, IngredientResponse.class));

        assertThat(ingredients)
                .extracting(IngredientResponse::name)
                .containsExactly("Café em grão", "Leite");
    }

    @Test
    void findsIngredientById() throws Exception {
        Ingredient ingredient = ingredientRepository.save(new Ingredient("Copo 300ml", IngredientUnit.UNIT));

        mockMvc.perform(get("/api/v1/ingredients/{id}", ingredient.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ingredient.getId().toString()))
                .andExpect(jsonPath("$.name").value("Copo 300ml"))
                .andExpect(jsonPath("$.unit").value("UNIT"));
    }

    @Test
    void returnsIngredientNotFoundWhenGettingUnknownId() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/ingredients/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void updatesIngredientAndPersistsChanges() throws Exception {
        Ingredient ingredient = ingredientRepository.save(new Ingredient("Leite", IngredientUnit.MILLILITER));

        String body = """
                {
                  "name": "Leite integral",
                  "unit": "MILLILITER",
                  "active": false
                }
                """;

        mockMvc.perform(put("/api/v1/ingredients/{id}", ingredient.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ingredient.getId().toString()))
                .andExpect(jsonPath("$.name").value("Leite integral"))
                .andExpect(jsonPath("$.unit").value("MILLILITER"))
                .andExpect(jsonPath("$.active").value(false));

        Optional<Ingredient> persisted = ingredientRepository.findById(ingredient.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getName()).isEqualTo("Leite integral");
        assertThat(persisted.get().isActive()).isFalse();
    }

    @Test
    void updatesIngredientUnit() throws Exception {
        Ingredient ingredient = ingredientRepository.save(new Ingredient("Ovo", IngredientUnit.UNIT));

        String body = """
                {
                  "name": "Ovo",
                  "unit": "GRAM",
                  "active": true
                }
                """;

        mockMvc.perform(put("/api/v1/ingredients/{id}", ingredient.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unit").value("GRAM"));

        Optional<Ingredient> persisted = ingredientRepository.findById(ingredient.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getUnit()).isEqualTo(IngredientUnit.GRAM);
    }

    @Test
    void returnsIngredientNotFoundWhenUpdatingUnknownIngredient() throws Exception {
        UUID missingId = UUID.randomUUID();

        String body = """
                {
                  "name": "Leite",
                  "unit": "MILLILITER",
                  "active": true
                }
                """;

        mockMvc.perform(put("/api/v1/ingredients/{id}", missingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsBlankNameOnUpdateWithStandardErrorFormat() throws Exception {
        Ingredient ingredient = ingredientRepository.save(new Ingredient("Leite", IngredientUnit.MILLILITER));

        String body = """
                {
                  "name": "",
                  "unit": "MILLILITER",
                  "active": true
                }
                """;

        mockMvc.perform(put("/api/v1/ingredients/{id}", ingredient.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsMissingActiveOnUpdateWithStandardErrorFormat() throws Exception {
        Ingredient ingredient = ingredientRepository.save(new Ingredient("Leite", IngredientUnit.MILLILITER));

        String body = """
                {
                  "name": "Leite",
                  "unit": "MILLILITER"
                }
                """;

        mockMvc.perform(put("/api/v1/ingredients/{id}", ingredient.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

}
