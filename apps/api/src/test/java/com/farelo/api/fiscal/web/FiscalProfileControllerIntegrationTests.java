package com.farelo.api.fiscal.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.ProductRepository;
import com.farelo.api.fiscal.FiscalProfile;
import com.farelo.api.fiscal.FiscalProfileRepository;
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
 */
@SpringBootTest
@AutoConfigureMockMvc
class FiscalProfileControllerIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FiscalProfileRepository fiscalProfileRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanFiscalProfileTable() {
        // product first — it FKs into fiscal_profile as of FARELO-151. See
        // this class's javadoc.
        productRepository.deleteAll();
        fiscalProfileRepository.deleteAll();
    }

    @Test
    void createsFiscalProfileAndPersistsIt() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/fiscal-profiles")
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Isento", "cfop": "510A"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsBlankNameWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/fiscal-profiles")
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
        mockMvc.perform(get("/api/v1/fiscal-profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void returnsAllCreatedFiscalProfilesSortedByName() throws Exception {
        fiscalProfileRepository.save(new FiscalProfile("Tributado padrão"));
        fiscalProfileRepository.save(new FiscalProfile("Isento"));

        MvcResult result = mockMvc.perform(get("/api/v1/fiscal-profiles"))
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

        mockMvc.perform(get("/api/v1/fiscal-profiles/{id}", fiscalProfile.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fiscalProfile.getId().toString()))
                .andExpect(jsonPath("$.name").value("Isento"));
    }

    @Test
    void returnsFiscalProfileNotFoundWhenGettingUnknownId() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/fiscal-profiles/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FISCAL_PROFILE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

}
