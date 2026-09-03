package com.farelo.api.fiscal.web;

import com.farelo.api.AbstractIntegrationTest;
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
 * tests that assert list contents clear it first. Unlike {@code
 * ingredient}, no other table has a DB-level FK into {@code
 * fiscal_profile} yet (FARELO-151, {@code Product.fiscalProfileId}, is a
 * future ticket), so a plain {@code fiscalProfileRepository.deleteAll()} is
 * safe here with no ordering landmine to document.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FiscalProfileControllerIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FiscalProfileRepository fiscalProfileRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanFiscalProfileTable() {
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
