package com.farelo.api.catalog.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
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

import java.math.BigDecimal;
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
 * /api/v1/products}, against a real PostgreSQL instance (Testcontainers).
 *
 * <p>Same reasoning as {@code CategoryControllerIntegrationTests}: the
 * shared singleton Postgres container (see {@link AbstractIntegrationTest})
 * means the {@code product}/{@code category} tables may already have rows
 * from other test classes, so tests that assert list contents clear both
 * tables first (products before categories, because of the FK).
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

    @BeforeEach
    void cleanCatalogTables() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

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

    @Test
    void returnsEmptyListWhenNoProductsExist() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void returnsAllCreatedProducts() throws Exception {
        Category category = categoryRepository.save(new Category("Bebidas"));
        Product espresso = new Product("Café Espresso", new BigDecimal("7.50"), category);
        Product suco = new Product("Suco de Laranja", new BigDecimal("9.00"), category);
        productRepository.save(espresso);
        productRepository.save(suco);

        MvcResult result = mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn();

        List<ProductResponse> products = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, ProductResponse.class));

        assertThat(products)
                .extracting(ProductResponse::name)
                .containsExactly("Café Espresso", "Suco de Laranja");
        assertThat(products)
                .extracting(ProductResponse::categoryId)
                .containsOnly(category.getId());
    }

    @Test
    void updatesProductAndPersistsChanges() throws Exception {
        Category original = categoryRepository.save(new Category("Bebidas"));
        Category newCategory = categoryRepository.save(new Category("Sobremesas"));
        Product product = productRepository.save(new Product("Café Espresso", new BigDecimal("7.50"), original));

        String body = """
                {
                  "name": "Café Espresso Duplo",
                  "description": "Dose dupla",
                  "price": 9.90,
                  "categoryId": "%s",
                  "imageUrl": "https://example.com/espresso-duplo.png",
                  "active": false
                }
                """.formatted(newCategory.getId());

        mockMvc.perform(put("/api/v1/products/{id}", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.getId().toString()))
                .andExpect(jsonPath("$.name").value("Café Espresso Duplo"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.categoryId").value(newCategory.getId().toString()));

        Optional<Product> persisted = productRepository.findById(product.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getName()).isEqualTo("Café Espresso Duplo");
        assertThat(persisted.get().getDescription()).isEqualTo("Dose dupla");
        assertThat(persisted.get().getPrice()).isEqualByComparingTo(new BigDecimal("9.90"));
        assertThat(persisted.get().isActive()).isFalse();
        assertThat(persisted.get().getCategory().getId()).isEqualTo(newCategory.getId());
    }

    @Test
    void returnsProductNotFoundWhenUpdatingUnknownProduct() throws Exception {
        Category category = categoryRepository.save(new Category("Bebidas"));
        UUID missingProductId = UUID.randomUUID();

        String body = """
                {
                  "name": "Café Espresso",
                  "price": 7.50,
                  "categoryId": "%s",
                  "active": true
                }
                """.formatted(category.getId());

        mockMvc.perform(put("/api/v1/products/{id}", missingProductId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsCategoryNotFoundWhenUpdatingWithUnknownCategory() throws Exception {
        Category category = categoryRepository.save(new Category("Bebidas"));
        Product product = productRepository.save(new Product("Café Espresso", new BigDecimal("7.50"), category));
        UUID missingCategoryId = UUID.randomUUID();

        String body = """
                {
                  "name": "Café Espresso",
                  "price": 7.50,
                  "categoryId": "%s",
                  "active": true
                }
                """.formatted(missingCategoryId);

        mockMvc.perform(put("/api/v1/products/{id}", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsNegativePriceOnUpdateWithStandardErrorFormat() throws Exception {
        Category category = categoryRepository.save(new Category("Bebidas"));
        Product product = productRepository.save(new Product("Café Espresso", new BigDecimal("7.50"), category));

        String body = """
                {
                  "name": "Café Espresso",
                  "price": -1.00,
                  "categoryId": "%s",
                  "active": true
                }
                """.formatted(category.getId());

        mockMvc.perform(put("/api/v1/products/{id}", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

}
