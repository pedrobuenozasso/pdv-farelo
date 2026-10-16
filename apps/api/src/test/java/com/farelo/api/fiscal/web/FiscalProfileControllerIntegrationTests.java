package com.farelo.api.fiscal.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.ProductRepository;
import com.farelo.api.fiscal.FiscalProfile;
import com.farelo.api.fiscal.FiscalProfileRepository;
import com.farelo.api.inventory.RecipeItemRepository;
import com.farelo.api.inventory.RecipeRepository;
import com.farelo.api.ordering.OrderItemRepository;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.JwtTokenService;
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
 * /api/v1/fiscal-profiles}, against a real PostgreSQL instance
 * (Testcontainers).
 *
 * <p>Same reasoning as {@code IngredientControllerIntegrationTests}/{@code
 * CategoryControllerIntegrationTests}: the shared singleton Postgres
 * container (see {@link AbstractIntegrationTest}) means the {@code
 * fiscal_profile} table may already have rows from other test classes, so
 * tests that assert list contents clear it first.
 *
 * <p><b>FARELO-151</b>: {@code product.fiscal_profile_id} now FKs into this
 * table (see {@code V28__add_product_fiscal_profile_id_column.sql}), so a
 * blind {@code fiscalProfileRepository.deleteAll()} is no longer
 * unconditionally safe — a leftover {@code Product} row from another test
 * class (e.g. {@code ProductControllerIntegrationTests}, run order is
 * Surefire's, not alphabetical) could still reference a fiscal profile this
 * class tries to delete. Same fix already applied by {@code
 * CategoryControllerIntegrationTests} for the exact same shape of problem
 * with {@code category}: {@code productRepository.deleteAll()} runs first,
 * in this class's own {@code @BeforeEach}, rather than relying on every
 * other class to clean up after itself.
 *
 * <p><b>{@code order_item.product_id}</b>: same reasoning one level deeper.
 * {@code OrderControllerIntegrationTests}' {@code createActiveProduct}/
 * {@code createOrder} helpers create real {@code Product}/{@code Order}/
 * {@code OrderItem} rows against the same shared Testcontainers Postgres
 * and never delete them (only {@code Command} status is reset in that
 * class's own {@code @AfterEach}) — a longstanding pattern, not something
 * this ticket introduced. It only surfaces here because Surefire's default
 * {@code runOrder} is filesystem-dependent (confirmed: passes locally on
 * macOS, failed in CI on Linux — same class list, different order), so
 * whether a leftover {@code OrderItem} exists when this class's
 * {@code @BeforeEach} runs is a matter of luck, not something to rely on.
 * {@code orderItemRepository.deleteAll()} runs before
 * {@code productRepository.deleteAll()} for the same reason the paragraph
 * above already gives for {@code fiscal_profile}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FiscalProfileControllerIntegrationTests extends AbstractIntegrationTest {

    private String adminToken;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FiscalProfileRepository fiscalProfileRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private RecipeItemRepository recipeItemRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @BeforeEach
    void cleanFiscalProfileTable() {
        // order_item and recipe_item/recipe first — both eventually FK into
        // product (recipe.product_id, order_item.product_id), which FKs
        // into fiscal_profile as of FARELO-151. recipe_item before recipe
        // (recipe_item.recipe_id). See this class's javadoc.
        orderItemRepository.deleteAll();
        recipeItemRepository.deleteAll();
        recipeRepository.deleteAll();
        productRepository.deleteAll();
        fiscalProfileRepository.deleteAll();
        adminToken = tokenFor(UserRole.ADMIN);
    }

    private String tokenFor(UserRole role) {
        User user = userRepository.save(new User(
                "Fiscal Test User",
                "fiscal-%s@farelo.dev".formatted(UUID.randomUUID()),
                passwordEncoder.encode("test-password"), role));
        return jwtTokenService.issue(user).token();
    }

    @Test
    void createsFiscalProfileAndPersistsIt() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/fiscal-profiles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Isento", "description": "Produtos sem incidência de ICMS"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Isento"))
                .andExpect(jsonPath("$.description").value("Produtos sem incidência de ICMS"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn();

        FiscalProfileResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), FiscalProfileResponse.class);

        Optional<FiscalProfile> persisted = fiscalProfileRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getName()).isEqualTo("Isento");
        assertThat(persisted.get().getDescription()).isEqualTo("Produtos sem incidência de ICMS");
        assertThat(persisted.get().isActive()).isTrue();
    }

    @Test
    void createsFiscalProfileWithoutDescription() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/fiscal-profiles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Tributado padrão"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andReturn();

        FiscalProfileResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), FiscalProfileResponse.class);

        Optional<FiscalProfile> persisted = fiscalProfileRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getDescription()).isNull();
    }

    // FARELO-152.
    @Test
    void createsFiscalProfileWithValidNcm() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/fiscal-profiles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Tributado padrão", "ncm": "12345678"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ncm").value("12345678"))
                .andReturn();

        FiscalProfileResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), FiscalProfileResponse.class);

        Optional<FiscalProfile> persisted = fiscalProfileRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getNcm()).isEqualTo("12345678");
    }

    // FARELO-152.
    @Test
    void createsFiscalProfileWithoutNcm() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/fiscal-profiles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Isento"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ncm").value(nullValue()))
                .andReturn();

        FiscalProfileResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), FiscalProfileResponse.class);

        Optional<FiscalProfile> persisted = fiscalProfileRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getNcm()).isNull();
    }

    // FARELO-152.
    @Test
    void rejectsNcmWithWrongDigitCountWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/fiscal-profiles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Isento", "ncm": "1234567"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // FARELO-152.
    @Test
    void rejectsNonNumericNcmWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/fiscal-profiles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Isento", "ncm": "1234567A"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // FARELO-153.
    @Test
    void createsFiscalProfileWithValidCfop() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/fiscal-profiles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Tributado padrão", "cfop": "5102"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cfop").value("5102"))
                .andReturn();

        FiscalProfileResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), FiscalProfileResponse.class);

        Optional<FiscalProfile> persisted = fiscalProfileRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getCfop()).isEqualTo("5102");
    }

    // FARELO-153.
    @Test
    void createsFiscalProfileWithoutCfop() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/fiscal-profiles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Isento"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cfop").value(nullValue()))
                .andReturn();

        FiscalProfileResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), FiscalProfileResponse.class);

        Optional<FiscalProfile> persisted = fiscalProfileRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getCfop()).isNull();
    }

    // FARELO-153.
    @Test
    void rejectsCfopWithWrongDigitCountWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/fiscal-profiles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Isento", "cfop": "510"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // FARELO-153.
    @Test
    void rejectsNonNumericCfopWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/fiscal-profiles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Isento", "cfop": "510A"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // FARELO-154.
    @Test
    void createsFiscalProfileWithValidCstAlone() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/fiscal-profiles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Tributado padrão", "cst": "60"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cst").value("60"))
                .andExpect(jsonPath("$.csosn").value(nullValue()))
                .andReturn();

        FiscalProfileResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), FiscalProfileResponse.class);

        Optional<FiscalProfile> persisted = fiscalProfileRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getCst()).isEqualTo("60");
        assertThat(persisted.get().getCsosn()).isNull();
    }

    // FARELO-154.
    @Test
    void createsFiscalProfileWithValidCsosnAlone() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/fiscal-profiles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Simples Nacional", "csosn": "102"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.csosn").value("102"))
                .andExpect(jsonPath("$.cst").value(nullValue()))
                .andReturn();

        FiscalProfileResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), FiscalProfileResponse.class);

        Optional<FiscalProfile> persisted = fiscalProfileRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getCsosn()).isEqualTo("102");
        assertThat(persisted.get().getCst()).isNull();
    }

    // FARELO-154.
    @Test
    void createsFiscalProfileWithoutCstOrCsosn() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/fiscal-profiles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Isento"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cst").value(nullValue()))
                .andExpect(jsonPath("$.csosn").value(nullValue()))
                .andReturn();

        FiscalProfileResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), FiscalProfileResponse.class);

        Optional<FiscalProfile> persisted = fiscalProfileRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getCst()).isNull();
        assertThat(persisted.get().getCsosn()).isNull();
    }

    // FARELO-154: CstCsosnMutuallyExclusive rejects both being set on the
    // same create request, same standard VALIDATION_ERROR shape as every
    // other Bean Validation failure on this endpoint.
    @Test
    void rejectsFiscalProfileWithBothCstAndCsosnSetWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/fiscal-profiles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Inválido", "cst": "60", "csosn": "102"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // FARELO-154.
    @Test
    void rejectsCstWithWrongDigitCountWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/fiscal-profiles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Isento", "cst": "6"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // FARELO-154.
    @Test
    void rejectsNonNumericCstWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/fiscal-profiles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Isento", "cst": "6A"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // FARELO-154.
    @Test
    void rejectsCsosnWithWrongDigitCountWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/fiscal-profiles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Isento", "csosn": "10"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // FARELO-154.
    @Test
    void rejectsNonNumericCsosnWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/fiscal-profiles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Isento", "csosn": "10A"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsBlankNameWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/fiscal-profiles")
                        .header("Authorization", "Bearer " + adminToken)
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
        mockMvc.perform(post("/api/v1/fiscal-profiles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "sem nome"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsEmptyListWhenNoFiscalProfilesExist() throws Exception {
        mockMvc.perform(get("/api/v1/fiscal-profiles")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void returnsAllCreatedFiscalProfilesSortedByName() throws Exception {
        fiscalProfileRepository.save(new FiscalProfile("Tributado padrão"));
        fiscalProfileRepository.save(new FiscalProfile("Isento"));

        MvcResult result = mockMvc.perform(get("/api/v1/fiscal-profiles")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn();

        List<FiscalProfileResponse> fiscalProfiles = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, FiscalProfileResponse.class));

        assertThat(fiscalProfiles)
                .extracting(FiscalProfileResponse::name)
                .containsExactly("Isento", "Tributado padrão");
    }

    @Test
    void findsFiscalProfileById() throws Exception {
        FiscalProfile fiscalProfile = fiscalProfileRepository.save(new FiscalProfile("Isento"));

        mockMvc.perform(get("/api/v1/fiscal-profiles/{id}", fiscalProfile.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fiscalProfile.getId().toString()))
                .andExpect(jsonPath("$.name").value("Isento"));
    }

    @Test
    void returnsFiscalProfileNotFoundWhenGettingUnknownId() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/fiscal-profiles/{id}", missingId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FISCAL_PROFILE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void protectsFiscalProfileReadsAndAllowsManager() throws Exception {
        UUID id = UUID.randomUUID();
        for (String path : List.of("/api/v1/fiscal-profiles", "/api/v1/fiscal-profiles/" + id)) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
            mockMvc.perform(get(path).header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        mockMvc.perform(get("/api/v1/fiscal-profiles")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.MANAGER)))
                .andExpect(status().isOk());
    }

    @Test
    void updatesFiscalProfileAndPersistsChanges() throws Exception {
        FiscalProfile fiscalProfile = fiscalProfileRepository.save(new FiscalProfile("Isento"));

        String body = """
                {
                  "name": "Isento (revisado)",
                  "description": "Atualizado após revisão contábil",
                  "active": false
                }
                """;

        mockMvc.perform(put("/api/v1/fiscal-profiles/{id}", fiscalProfile.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fiscalProfile.getId().toString()))
                .andExpect(jsonPath("$.name").value("Isento (revisado)"))
                .andExpect(jsonPath("$.description").value("Atualizado após revisão contábil"))
                .andExpect(jsonPath("$.active").value(false));

        Optional<FiscalProfile> persisted = fiscalProfileRepository.findById(fiscalProfile.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getName()).isEqualTo("Isento (revisado)");
        assertThat(persisted.get().isActive()).isFalse();
    }

    @Test
    void updateWithoutDescriptionClearsPreviouslySetDescription() throws Exception {
        FiscalProfile fiscalProfile = fiscalProfileRepository.save(new FiscalProfile("Isento"));
        fiscalProfile.setDescription("Descrição original");
        fiscalProfileRepository.save(fiscalProfile);

        String body = """
                {
                  "name": "Isento",
                  "active": true
                }
                """;

        mockMvc.perform(put("/api/v1/fiscal-profiles/{id}", fiscalProfile.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(nullValue()));

        Optional<FiscalProfile> persisted = fiscalProfileRepository.findById(fiscalProfile.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getDescription()).isNull();
    }

    // FARELO-152.
    @Test
    void updatesFiscalProfileSettingNcm() throws Exception {
        FiscalProfile fiscalProfile = fiscalProfileRepository.save(new FiscalProfile("Isento"));

        String body = """
                {
                  "name": "Isento",
                  "active": true,
                  "ncm": "87654321"
                }
                """;

        mockMvc.perform(put("/api/v1/fiscal-profiles/{id}", fiscalProfile.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ncm").value("87654321"));

        Optional<FiscalProfile> persisted = fiscalProfileRepository.findById(fiscalProfile.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getNcm()).isEqualTo("87654321");
    }

    // FARELO-152.
    @Test
    void updateWithoutNcmClearsPreviouslySetNcm() throws Exception {
        FiscalProfile fiscalProfile = fiscalProfileRepository.save(new FiscalProfile("Isento"));
        fiscalProfile.setNcm("12345678");
        fiscalProfileRepository.save(fiscalProfile);

        String body = """
                {
                  "name": "Isento",
                  "active": true
                }
                """;

        mockMvc.perform(put("/api/v1/fiscal-profiles/{id}", fiscalProfile.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ncm").value(nullValue()));

        Optional<FiscalProfile> persisted = fiscalProfileRepository.findById(fiscalProfile.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getNcm()).isNull();
    }

    // FARELO-152.
    @Test
    void rejectsInvalidNcmOnUpdateWithStandardErrorFormat() throws Exception {
        FiscalProfile fiscalProfile = fiscalProfileRepository.save(new FiscalProfile("Isento"));

        String body = """
                {
                  "name": "Isento",
                  "active": true,
                  "ncm": "abc"
                }
                """;

        mockMvc.perform(put("/api/v1/fiscal-profiles/{id}", fiscalProfile.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // FARELO-153.
    @Test
    void updatesFiscalProfileSettingCfop() throws Exception {
        FiscalProfile fiscalProfile = fiscalProfileRepository.save(new FiscalProfile("Isento"));

        String body = """
                {
                  "name": "Isento",
                  "active": true,
                  "cfop": "6102"
                }
                """;

        mockMvc.perform(put("/api/v1/fiscal-profiles/{id}", fiscalProfile.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cfop").value("6102"));

        Optional<FiscalProfile> persisted = fiscalProfileRepository.findById(fiscalProfile.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getCfop()).isEqualTo("6102");
    }

    // FARELO-153.
    @Test
    void updateWithoutCfopClearsPreviouslySetCfop() throws Exception {
        FiscalProfile fiscalProfile = fiscalProfileRepository.save(new FiscalProfile("Isento"));
        fiscalProfile.setCfop("5102");
        fiscalProfileRepository.save(fiscalProfile);

        String body = """
                {
                  "name": "Isento",
                  "active": true
                }
                """;

        mockMvc.perform(put("/api/v1/fiscal-profiles/{id}", fiscalProfile.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cfop").value(nullValue()));

        Optional<FiscalProfile> persisted = fiscalProfileRepository.findById(fiscalProfile.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getCfop()).isNull();
    }

    // FARELO-153.
    @Test
    void rejectsInvalidCfopOnUpdateWithStandardErrorFormat() throws Exception {
        FiscalProfile fiscalProfile = fiscalProfileRepository.save(new FiscalProfile("Isento"));

        String body = """
                {
                  "name": "Isento",
                  "active": true,
                  "cfop": "abc"
                }
                """;

        mockMvc.perform(put("/api/v1/fiscal-profiles/{id}", fiscalProfile.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // FARELO-154.
    @Test
    void updatesFiscalProfileSettingCst() throws Exception {
        FiscalProfile fiscalProfile = fiscalProfileRepository.save(new FiscalProfile("Isento"));

        String body = """
                {
                  "name": "Isento",
                  "active": true,
                  "cst": "40"
                }
                """;

        mockMvc.perform(put("/api/v1/fiscal-profiles/{id}", fiscalProfile.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cst").value("40"))
                .andExpect(jsonPath("$.csosn").value(nullValue()));

        Optional<FiscalProfile> persisted = fiscalProfileRepository.findById(fiscalProfile.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getCst()).isEqualTo("40");
        assertThat(persisted.get().getCsosn()).isNull();
    }

    // FARELO-154.
    @Test
    void updatesFiscalProfileSettingCsosn() throws Exception {
        FiscalProfile fiscalProfile = fiscalProfileRepository.save(new FiscalProfile("Isento"));

        String body = """
                {
                  "name": "Isento",
                  "active": true,
                  "csosn": "500"
                }
                """;

        mockMvc.perform(put("/api/v1/fiscal-profiles/{id}", fiscalProfile.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.csosn").value("500"))
                .andExpect(jsonPath("$.cst").value(nullValue()));

        Optional<FiscalProfile> persisted = fiscalProfileRepository.findById(fiscalProfile.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getCsosn()).isEqualTo("500");
        assertThat(persisted.get().getCst()).isNull();
    }

    // FARELO-154.
    @Test
    void updateWithoutCstClearsPreviouslySetCst() throws Exception {
        FiscalProfile fiscalProfile = fiscalProfileRepository.save(new FiscalProfile("Isento"));
        fiscalProfile.setCst("60");
        fiscalProfileRepository.save(fiscalProfile);

        String body = """
                {
                  "name": "Isento",
                  "active": true
                }
                """;

        mockMvc.perform(put("/api/v1/fiscal-profiles/{id}", fiscalProfile.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cst").value(nullValue()));

        Optional<FiscalProfile> persisted = fiscalProfileRepository.findById(fiscalProfile.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getCst()).isNull();
    }

    // FARELO-154.
    @Test
    void updateWithoutCsosnClearsPreviouslySetCsosn() throws Exception {
        FiscalProfile fiscalProfile = fiscalProfileRepository.save(new FiscalProfile("Isento"));
        fiscalProfile.setCsosn("102");
        fiscalProfileRepository.save(fiscalProfile);

        String body = """
                {
                  "name": "Isento",
                  "active": true
                }
                """;

        mockMvc.perform(put("/api/v1/fiscal-profiles/{id}", fiscalProfile.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.csosn").value(nullValue()));

        Optional<FiscalProfile> persisted = fiscalProfileRepository.findById(fiscalProfile.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getCsosn()).isNull();
    }

    // FARELO-154: CstCsosnMutuallyExclusive rejects both being set on an
    // update request too, not just on create.
    @Test
    void rejectsUpdateSettingBothCstAndCsosnWithStandardErrorFormat() throws Exception {
        FiscalProfile fiscalProfile = fiscalProfileRepository.save(new FiscalProfile("Isento"));

        String body = """
                {
                  "name": "Isento",
                  "active": true,
                  "cst": "60",
                  "csosn": "102"
                }
                """;

        mockMvc.perform(put("/api/v1/fiscal-profiles/{id}", fiscalProfile.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // FARELO-154.
    @Test
    void rejectsInvalidCstOnUpdateWithStandardErrorFormat() throws Exception {
        FiscalProfile fiscalProfile = fiscalProfileRepository.save(new FiscalProfile("Isento"));

        String body = """
                {
                  "name": "Isento",
                  "active": true,
                  "cst": "abc"
                }
                """;

        mockMvc.perform(put("/api/v1/fiscal-profiles/{id}", fiscalProfile.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // FARELO-154.
    @Test
    void rejectsInvalidCsosnOnUpdateWithStandardErrorFormat() throws Exception {
        FiscalProfile fiscalProfile = fiscalProfileRepository.save(new FiscalProfile("Isento"));

        String body = """
                {
                  "name": "Isento",
                  "active": true,
                  "csosn": "abc"
                }
                """;

        mockMvc.perform(put("/api/v1/fiscal-profiles/{id}", fiscalProfile.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void returnsFiscalProfileNotFoundWhenUpdatingUnknownFiscalProfile() throws Exception {
        UUID missingId = UUID.randomUUID();

        String body = """
                {
                  "name": "Isento",
                  "active": true
                }
                """;

        mockMvc.perform(put("/api/v1/fiscal-profiles/{id}", missingId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FISCAL_PROFILE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsBlankNameOnUpdateWithStandardErrorFormat() throws Exception {
        FiscalProfile fiscalProfile = fiscalProfileRepository.save(new FiscalProfile("Isento"));

        String body = """
                {
                  "name": "",
                  "active": true
                }
                """;

        mockMvc.perform(put("/api/v1/fiscal-profiles/{id}", fiscalProfile.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsMissingActiveOnUpdateWithStandardErrorFormat() throws Exception {
        FiscalProfile fiscalProfile = fiscalProfileRepository.save(new FiscalProfile("Isento"));

        String body = """
                {
                  "name": "Isento"
                }
                """;

        mockMvc.perform(put("/api/v1/fiscal-profiles/{id}", fiscalProfile.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsAnonymousFiscalProfileWrites() throws Exception {
        mockMvc.perform(post("/api/v1/fiscal-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Isento\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(put("/api/v1/fiscal-profiles/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Isento\",\"active\":true}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        assertThat(fiscalProfileRepository.count()).isZero();
    }

    @Test
    void rejectsNonAdminFiscalProfileWrites() throws Exception {
        for (UserRole role : List.of(UserRole.MANAGER, UserRole.CASHIER)) {
            String token = tokenFor(role);
            mockMvc.perform(post("/api/v1/fiscal-profiles")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Isento\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            mockMvc.perform(put("/api/v1/fiscal-profiles/{id}", UUID.randomUUID())
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Isento\",\"active\":true}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        assertThat(fiscalProfileRepository.count()).isZero();
    }
}
