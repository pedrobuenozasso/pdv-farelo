package com.farelo.api.catalog.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.ProductRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code POST}/{@code GET /api/v1/categories}, against
 * a real PostgreSQL instance (Testcontainers).
 *
 * <p>{@link AbstractIntegrationTest} shares a single PostgreSQL container
 * (and therefore the same tables) across every test class in the run, so
 * each test here clears the catalog tables first to get a deterministic
 * starting point — otherwise "empty list" assertions would be flaky
 * depending on what other test classes already persisted. Products are
 * deleted before categories because of the FK.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CategoryControllerIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanCatalogTables() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    void createsCategoryAndPersistsIt() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Sobremesas"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Sobremesas"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn();

        CategoryResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), CategoryResponse.class);

        Optional<Category> persisted = categoryRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getName()).isEqualTo("Sobremesas");
        assertThat(persisted.get().isActive()).isTrue();
    }

    @Test
    void rejectsBlankNameWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsMissingNameWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsEmptyListWhenNoCategoriesExist() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void returnsAllCreatedCategories() throws Exception {
        categoryRepository.save(new Category("Sobremesas"));
        categoryRepository.save(new Category("Bebidas"));

        MvcResult result = mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn();

        List<CategoryResponse> categories = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, CategoryResponse.class));

        assertThat(categories)
                .extracting(CategoryResponse::name)
                .containsExactly("Bebidas", "Sobremesas");
    }

}
