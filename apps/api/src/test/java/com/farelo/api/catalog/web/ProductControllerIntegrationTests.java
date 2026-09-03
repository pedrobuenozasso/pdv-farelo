package com.farelo.api.catalog.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.audit.AuditLog;
import com.farelo.api.audit.AuditLogRepository;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import com.farelo.api.catalog.ProductionStation;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.JwtTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
 *
 * <p><b>FARELO-123</b>: {@code POST}/{@code PUT} now require
 * {@link UserRole#ADMIN}/{@link UserRole#MANAGER} (see
 * {@code ProductController}'s javadoc), so every {@code POST}/{@code PUT}
 * here mints a real token via {@link #tokenFor} and sends it as
 * {@code Authorization: Bearer <token>} — same pattern as
 * {@code CategoryControllerIntegrationTests}. {@code GET} is deliberately
 * left with <b>no</b> header anywhere in this class — see the controller
 * javadoc for why it stays unprotected (public "Cardápio QR" dependency).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerIntegrationTests extends AbstractIntegrationTest {

    private static final String PASSWORD = "senha-forte-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void cleanCatalogTables() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    private String tokenFor(UserRole role) {
        User user = userRepository.save(new User(
                "Test User",
                "test-%s@farelo.dev".formatted(UUID.randomUUID()),
                passwordEncoder.encode(PASSWORD),
                role));
        return jwtTokenService.issue(user).token();
    }

    // FARELO-126: same as tokenFor(UserRole), but also hands back the
    // persisted User itself — the audit tests below need to assert the
    // AuditLog row's userName/userEmail snapshot actually matches the
    // caller who made the request, which a bare token string can't answer.
    private record AuthenticatedTestUser(User user, String token) {
    }

    private AuthenticatedTestUser userAndTokenFor(UserRole role, String name) {
        User user = userRepository.save(new User(
                name,
                "test-%s@farelo.dev".formatted(UUID.randomUUID()),
                passwordEncoder.encode(PASSWORD),
                role));
        return new AuthenticatedTestUser(user, jwtTokenService.issue(user).token());
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
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Café Espresso"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.availableOnMenu").value(true))
                .andExpect(jsonPath("$.availableOnPos").value(true))
                .andExpect(jsonPath("$.categoryId").value(category.getId().toString()))
                .andExpect(jsonPath("$.productionStation").value(nullValue()))
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
        // availableOnMenu/availableOnPos omitted from the request body above
        // -> default true (FARELO-017).
        assertThat(persisted.get().isAvailableOnMenu()).isTrue();
        assertThat(persisted.get().isAvailableOnPos()).isTrue();
        // productionStation omitted from the request body above -> stays
        // null (FARELO-073, "not yet assigned" — no default is applied).
        assertThat(persisted.get().getProductionStation()).isNull();
    }

    @Test
    void createsProductWithExplicitProductionStation() throws Exception {
        Category category = categoryRepository.save(new Category("Bebidas"));

        String body = """
                {
                  "name": "Cappuccino",
                  "price": 12.00,
                  "categoryId": "%s",
                  "productionStation": "BAR"
                }
                """.formatted(category.getId());

        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productionStation").value("BAR"))
                .andReturn();

        ProductResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ProductResponse.class);

        Optional<Product> persisted = productRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getProductionStation()).isEqualTo(ProductionStation.BAR);
    }

    @Test
    void createsProductWithExplicitAvailabilityValues() throws Exception {
        Category category = categoryRepository.save(new Category("Bebidas"));

        String body = """
                {
                  "name": "Café Espresso",
                  "price": 7.50,
                  "categoryId": "%s",
                  "availableOnMenu": false,
                  "availableOnPos": true
                }
                """.formatted(category.getId());

        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.availableOnMenu").value(false))
                .andExpect(jsonPath("$.availableOnPos").value(true))
                .andReturn();

        ProductResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ProductResponse.class);

        Optional<Product> persisted = productRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().isAvailableOnMenu()).isFalse();
        assertThat(persisted.get().isAvailableOnPos()).isTrue();
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
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
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
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
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
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
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
                  "active": false,
                  "availableOnMenu": false,
                  "availableOnPos": true
                }
                """.formatted(newCategory.getId());

        mockMvc.perform(put("/api/v1/products/{id}", product.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.getId().toString()))
                .andExpect(jsonPath("$.name").value("Café Espresso Duplo"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.availableOnMenu").value(false))
                .andExpect(jsonPath("$.availableOnPos").value(true))
                .andExpect(jsonPath("$.categoryId").value(newCategory.getId().toString()));

        Optional<Product> persisted = productRepository.findById(product.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getName()).isEqualTo("Café Espresso Duplo");
        assertThat(persisted.get().getDescription()).isEqualTo("Dose dupla");
        assertThat(persisted.get().getPrice()).isEqualByComparingTo(new BigDecimal("9.90"));
        assertThat(persisted.get().isActive()).isFalse();
        // availableOnMenu and availableOnPos updated independently — different
        // values from each other, to make sure they aren't accidentally tied
        // together in ProductService.update.
        assertThat(persisted.get().isAvailableOnMenu()).isFalse();
        assertThat(persisted.get().isAvailableOnPos()).isTrue();
        assertThat(persisted.get().getCategory().getId()).isEqualTo(newCategory.getId());
    }

    @Test
    void updatesAvailabilityFieldsIndependently() throws Exception {
        Category category = categoryRepository.save(new Category("Bebidas"));
        Product product = productRepository.save(new Product("Café Espresso", new BigDecimal("7.50"), category));
        assertThat(product.isAvailableOnMenu()).isTrue();
        assertThat(product.isAvailableOnPos()).isTrue();

        String body = """
                {
                  "name": "Café Espresso",
                  "price": 7.50,
                  "categoryId": "%s",
                  "active": true,
                  "availableOnMenu": true,
                  "availableOnPos": false
                }
                """.formatted(category.getId());

        mockMvc.perform(put("/api/v1/products/{id}", product.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableOnMenu").value(true))
                .andExpect(jsonPath("$.availableOnPos").value(false));

        Optional<Product> persisted = productRepository.findById(product.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().isAvailableOnMenu()).isTrue();
        assertThat(persisted.get().isAvailableOnPos()).isFalse();
    }

    @Test
    void updatesProductSettingProductionStation() throws Exception {
        Category category = categoryRepository.save(new Category("Comidas"));
        Product product = productRepository.save(new Product("Croissant", new BigDecimal("14.00"), category));
        assertThat(product.getProductionStation()).isNull();

        String body = """
                {
                  "name": "Croissant",
                  "price": 14.00,
                  "categoryId": "%s",
                  "active": true,
                  "availableOnMenu": true,
                  "availableOnPos": true,
                  "productionStation": "KITCHEN"
                }
                """.formatted(category.getId());

        mockMvc.perform(put("/api/v1/products/{id}", product.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productionStation").value("KITCHEN"));

        Optional<Product> persisted = productRepository.findById(product.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getProductionStation()).isEqualTo(ProductionStation.KITCHEN);
    }

    @Test
    void updatesProductClearingProductionStation() throws Exception {
        Category category = categoryRepository.save(new Category("Bebidas"));
        Product product = new Product("Cappuccino", new BigDecimal("12.00"), category);
        product.setProductionStation(ProductionStation.BAR);
        product = productRepository.save(product);
        assertThat(product.getProductionStation()).isEqualTo(ProductionStation.BAR);

        // productionStation omitted here -> deserializes to null on
        // ProductUpdateRequest, and a full-replace PUT applies that null
        // (see ProductService.update's javadoc-style comment).
        String body = """
                {
                  "name": "Cappuccino",
                  "price": 12.00,
                  "categoryId": "%s",
                  "active": true,
                  "availableOnMenu": true,
                  "availableOnPos": true
                }
                """.formatted(category.getId());

        mockMvc.perform(put("/api/v1/products/{id}", product.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productionStation").value(nullValue()));

        Optional<Product> persisted = productRepository.findById(product.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getProductionStation()).isNull();
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
                  "active": true,
                  "availableOnMenu": true,
                  "availableOnPos": true
                }
                """.formatted(category.getId());

        mockMvc.perform(put("/api/v1/products/{id}", missingProductId)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
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
                  "active": true,
                  "availableOnMenu": true,
                  "availableOnPos": true
                }
                """.formatted(missingCategoryId);

        mockMvc.perform(put("/api/v1/products/{id}", product.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
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
                  "active": true,
                  "availableOnMenu": true,
                  "availableOnPos": true
                }
                """.formatted(category.getId());

        mockMvc.perform(put("/api/v1/products/{id}", product.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // --- FARELO-123: RBAC on create()/update() ---------------------------

    @Test
    void createsProductAsManager() throws Exception {
        Category category = categoryRepository.save(new Category("Bebidas"));

        String body = """
                {
                  "name": "Café Espresso",
                  "price": 7.50,
                  "categoryId": "%s"
                }
                """.formatted(category.getId());

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsCreateWithNoAuthorizationHeader() throws Exception {
        Category category = categoryRepository.save(new Category("Bebidas"));

        String body = """
                {
                  "name": "Café Espresso",
                  "price": 7.50,
                  "categoryId": "%s"
                }
                """.formatted(category.getId());

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsCreateWhenCallerRoleIsNotAllowed() throws Exception {
        Category category = categoryRepository.save(new Category("Bebidas"));

        String body = """
                {
                  "name": "Café Espresso",
                  "price": 7.50,
                  "categoryId": "%s"
                }
                """.formatted(category.getId());

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.KITCHEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void updatesProductAsManager() throws Exception {
        Category category = categoryRepository.save(new Category("Bebidas"));
        Product product = productRepository.save(new Product("Café Espresso", new BigDecimal("7.50"), category));

        String body = """
                {
                  "name": "Café Espresso",
                  "price": 7.50,
                  "categoryId": "%s",
                  "active": true,
                  "availableOnMenu": true,
                  "availableOnPos": true
                }
                """.formatted(category.getId());

        mockMvc.perform(put("/api/v1/products/{id}", product.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsUpdateWithNoAuthorizationHeader() throws Exception {
        Category category = categoryRepository.save(new Category("Bebidas"));
        Product product = productRepository.save(new Product("Café Espresso", new BigDecimal("7.50"), category));

        String body = """
                {
                  "name": "Café Espresso",
                  "price": 7.50,
                  "categoryId": "%s",
                  "active": true,
                  "availableOnMenu": true,
                  "availableOnPos": true
                }
                """.formatted(category.getId());

        mockMvc.perform(put("/api/v1/products/{id}", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsUpdateWhenCallerRoleIsNotAllowed() throws Exception {
        Category category = categoryRepository.save(new Category("Bebidas"));
        Product product = productRepository.save(new Product("Café Espresso", new BigDecimal("7.50"), category));

        String body = """
                {
                  "name": "Café Espresso",
                  "price": 7.50,
                  "categoryId": "%s",
                  "active": true,
                  "availableOnMenu": true,
                  "availableOnPos": true
                }
                """.formatted(category.getId());

        mockMvc.perform(put("/api/v1/products/{id}", product.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // --- FARELO-126: auditing a price change ------------------------------

    @Test
    void updatingPriceRecordsAuditLogWithOldAndNewPriceAndActor() throws Exception {
        Category category = categoryRepository.save(new Category("Bebidas"));
        Product product = productRepository.save(new Product("Café Espresso", new BigDecimal("7.50"), category));
        AuthenticatedTestUser actor = userAndTokenFor(UserRole.ADMIN, "Gerente Ana");

        String body = """
                {
                  "name": "Café Espresso",
                  "price": 9.90,
                  "categoryId": "%s",
                  "active": true,
                  "availableOnMenu": true,
                  "availableOnPos": true
                }
                """.formatted(category.getId());

        mockMvc.perform(put("/api/v1/products/{id}", product.getId())
                        .header("Authorization", "Bearer " + actor.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        List<AuditLog> auditLogs = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "Product", product.getId());
        assertThat(auditLogs).hasSize(1);

        AuditLog auditLog = auditLogs.get(0);
        assertThat(auditLog.getAction()).isEqualTo("PRICE_CHANGED");
        assertThat(auditLog.getUserId()).isEqualTo(actor.user().getId());
        assertThat(auditLog.getUserName()).isEqualTo("Gerente Ana");
        assertThat(auditLog.getUserEmail()).isEqualTo(actor.user().getEmail());

        JsonNode previousValue = objectMapper.readTree(auditLog.getPreviousValue());
        JsonNode newValue = objectMapper.readTree(auditLog.getNewValue());
        assertThat(new BigDecimal(previousValue.get("price").asText())).isEqualByComparingTo("7.50");
        assertThat(new BigDecimal(newValue.get("price").asText())).isEqualByComparingTo("9.90");
    }

    @Test
    void updatingProductWithoutChangingPriceRecordsNoAuditLog() throws Exception {
        Category category = categoryRepository.save(new Category("Bebidas"));
        Product product = productRepository.save(new Product("Café Espresso", new BigDecimal("7.50"), category));

        // Same price (7.50), only the name changes — must not produce a
        // PRICE_CHANGED audit row (see ProductService#update's javadoc: the
        // comparison is BigDecimal#compareTo, so this also proves 7.50 vs
        // 7.5 would count as unchanged, not just a byte-identical resend).
        String body = """
                {
                  "name": "Café Espresso Duplo",
                  "price": 7.50,
                  "categoryId": "%s",
                  "active": true,
                  "availableOnMenu": true,
                  "availableOnPos": true
                }
                """.formatted(category.getId());

        mockMvc.perform(put("/api/v1/products/{id}", product.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        List<AuditLog> auditLogs = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "Product", product.getId());
        assertThat(auditLogs).isEmpty();
    }

    @Test
    void auditLogForPriceChangeIsQueryableViaAuditLogsEndpoint() throws Exception {
        Category category = categoryRepository.save(new Category("Bebidas"));
        Product product = productRepository.save(new Product("Café Espresso", new BigDecimal("7.50"), category));

        String body = """
                {
                  "name": "Café Espresso",
                  "price": 8.00,
                  "categoryId": "%s",
                  "active": true,
                  "availableOnMenu": true,
                  "availableOnPos": true
                }
                """.formatted(category.getId());

        mockMvc.perform(put("/api/v1/products/{id}", product.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // GET /api/v1/audit-logs stays unprotected at its own first ticket
        // (FARELO-125) — no Authorization header here on purpose, same as
        // every other test exercising that endpoint.
        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("entityType", "Product")
                        .param("entityId", product.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].action").value("PRICE_CHANGED"))
                .andExpect(jsonPath("$[0].entityType").value("Product"))
                .andExpect(jsonPath("$[0].entityId").value(product.getId().toString()));
    }

}
