package com.farelo.api.catalog.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code POST /api/v1/products}, against a real
 * PostgreSQL instance (Testcontainers).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsProductAndPersistsIt() throws Exception {
        Category category = categoryRepository.save(new Category("Bebidas"));

        String body = """
                {
                  "name": "Café Espresso",
                  "description": "Espresso curto, torra média",
                  "price": 7.50,
                  "categoryId": "%s",
                  "imageUrl": "https://example.com/espresso.png"
                }
                """.formatted(category.getId());

        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Café Espresso"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.categoryId").value(category.getId().toString()))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn();

        ProductResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ProductResponse.class);

        Optional<Product> persisted = productRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getName()).isEqualTo("Café Espresso");
        // BigDecimal price: compare by value, not by scale (AGENTS.md: money
        // is always BigDecimal) — same reasoning as ProductRepositoryIntegrationTests.
        assertThat(persisted.get().getPrice()).isEqualByComparingTo(new BigDecimal("7.50"));
        assertThat(persisted.get().getCategory().getId()).isEqualTo(category.getId());
    }

    @Test
    void returnsCategoryNotFoundWhenCategoryDoesNotExist() throws Exception {
        UUID missingCategoryId = UUID.randomUUID();

        String body = """
                {
                  "name": "Café Espresso",
                  "price": 7.50,
                  "categoryId": "%s"
                }
                """.formatted(missingCategoryId);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsMissingRequiredFieldsWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsNegativePriceWithStandardErrorFormat() throws Exception {
        Category category = categoryRepository.save(new Category("Doces"));

        String body = """
                {
                  "name": "Brigadeiro",
                  "price": -1.00,
                  "categoryId": "%s"
                }
                """.formatted(category.getId());

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

}
