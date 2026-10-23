package com.farelo.api.fiscal.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.fiscal.CompanyFiscalConfiguration;
import com.farelo.api.fiscal.CompanyFiscalConfigurationRepository;
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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code GET}/{@code PUT
 * /api/v1/company-fiscal-configuration} (FARELO-155), against a real
 * PostgreSQL instance (Testcontainers).
 *
 * <p>Same reasoning as {@code FiscalProfileControllerIntegrationTests}: the
 * shared singleton Postgres container (see {@link AbstractIntegrationTest})
 * means {@code company_fiscal_configuration} may already have a row left
 * over from another test class, so every test clears it first. Unlike
 * {@code fiscal_profile}, nothing FKs into this table, so a plain {@code
 * deleteAll()} is unconditionally safe here (no cross-table cleanup
 * landmine).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CompanyFiscalConfigurationControllerIntegrationTests extends AbstractIntegrationTest {

    private String adminToken;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyFiscalConfigurationRepository companyFiscalConfigurationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @BeforeEach
    void cleanTable() {
        companyFiscalConfigurationRepository.deleteAll();
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
    void returnsNotFoundWhenNotConfiguredYet() throws Exception {
        mockMvc.perform(get("/api/v1/company-fiscal-configuration")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMPANY_FISCAL_CONFIGURATION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void putCreatesConfigurationAndPersistsIt() throws Exception {
        MvcResult result = mockMvc.perform(put("/api/v1/company-fiscal-configuration")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cnpj": "12.345.678/0001-90",
                                  "legalName": "Farelo Comércio de Alimentos LTDA",
                                  "tradeName": "Farelo Café",
                                  "stateRegistration": "123.456.789.112",
                                  "address": "Rua das Flores, 123 - São Paulo/SP"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.cnpj").value("12.345.678/0001-90"))
                .andExpect(jsonPath("$.legalName").value("Farelo Comércio de Alimentos LTDA"))
                .andExpect(jsonPath("$.tradeName").value("Farelo Café"))
                .andExpect(jsonPath("$.stateRegistration").value("123.456.789.112"))
                .andExpect(jsonPath("$.address").value("Rua das Flores, 123 - São Paulo/SP"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn();

        CompanyFiscalConfigurationResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), CompanyFiscalConfigurationResponse.class);

        Optional<CompanyFiscalConfiguration> persisted = companyFiscalConfigurationRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getCnpj()).isEqualTo("12.345.678/0001-90");
        assertThat(persisted.get().getLegalName()).isEqualTo("Farelo Comércio de Alimentos LTDA");

        assertThat(companyFiscalConfigurationRepository.count()).isEqualTo(1);
    }

    @Test
    void putWithoutOptionalFieldsLeavesThemNull() throws Exception {
        mockMvc.perform(put("/api/v1/company-fiscal-configuration")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cnpj": "12.345.678/0001-90",
                                  "legalName": "Farelo Comércio de Alimentos LTDA"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeName").value(nullValue()))
                .andExpect(jsonPath("$.stateRegistration").value(nullValue()))
                .andExpect(jsonPath("$.address").value(nullValue()));
    }

    @Test
    void putPersistsExplicitNfceIssuerFieldsWithoutDerivingThemFromFreeText() throws Exception {
        mockMvc.perform(put("/api/v1/company-fiscal-configuration")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cnpj": "12.345.678/0001-90",
                                  "legalName": "Farelo Comércio de Alimentos LTDA",
                                  "address": "Texto legado",
                                  "taxRegime": "1",
                                  "street": "Rua das Flores",
                                  "streetNumber": "123",
                                  "district": "Centro",
                                  "cityCode": "3550308",
                                  "city": "São Paulo",
                                  "postalCode": "01001000"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxRegime").value("1"))
                .andExpect(jsonPath("$.street").value("Rua das Flores"))
                .andExpect(jsonPath("$.cityCode").value("3550308"))
                .andExpect(jsonPath("$.postalCode").value("01001000"));

        CompanyFiscalConfiguration stored = companyFiscalConfigurationRepository
                .findFirstByOrderByCreatedAtAsc().orElseThrow();
        assertThat(stored.getTaxRegime()).isEqualTo("1");
        assertThat(stored.getStreetNumber()).isEqualTo("123");
        assertThat(stored.getDistrict()).isEqualTo("Centro");
    }

    @Test
    void putRejectsMalformedIssuerCodes() throws Exception {
        mockMvc.perform(put("/api/v1/company-fiscal-configuration")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cnpj": "12.345.678/0001-90",
                                  "legalName": "Farelo Comércio de Alimentos LTDA",
                                  "taxRegime": "2",
                                  "cityCode": "35503",
                                  "postalCode": "01001"
                                }
                                """))
                .andExpect(status().isBadRequest());
        assertThat(companyFiscalConfigurationRepository.count()).isZero();
    }

    @Test
    void getReturnsThePreviouslySetConfiguration() throws Exception {
        mockMvc.perform(put("/api/v1/company-fiscal-configuration")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cnpj": "12.345.678/0001-90",
                                  "legalName": "Farelo Comércio de Alimentos LTDA"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/company-fiscal-configuration")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cnpj").value("12.345.678/0001-90"))
                .andExpect(jsonPath("$.legalName").value("Farelo Comércio de Alimentos LTDA"));
    }

    @Test
    void secondPutReplacesTheExistingRowInsteadOfCreatingASecondOne() throws Exception {
        mockMvc.perform(put("/api/v1/company-fiscal-configuration")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cnpj": "12.345.678/0001-90",
                                  "legalName": "Farelo Comércio de Alimentos LTDA"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/company-fiscal-configuration")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cnpj": "12.345.678/0001-90",
                                  "legalName": "Farelo Comércio de Alimentos LTDA (revisado)",
                                  "tradeName": "Farelo Café"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalName").value("Farelo Comércio de Alimentos LTDA (revisado)"))
                .andExpect(jsonPath("$.tradeName").value("Farelo Café"));

        assertThat(companyFiscalConfigurationRepository.count()).isEqualTo(1);
    }

    @Test
    void secondPutWithoutOptionalFieldClearsPreviouslySetValue() throws Exception {
        mockMvc.perform(put("/api/v1/company-fiscal-configuration")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cnpj": "12.345.678/0001-90",
                                  "legalName": "Farelo Comércio de Alimentos LTDA",
                                  "tradeName": "Farelo Café"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/company-fiscal-configuration")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cnpj": "12.345.678/0001-90",
                                  "legalName": "Farelo Comércio de Alimentos LTDA"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeName").value(nullValue()));
    }

    @Test
    void rejectsBlankCnpjWithStandardErrorFormat() throws Exception {
        mockMvc.perform(put("/api/v1/company-fiscal-configuration")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cnpj": "", "legalName": "Farelo Comércio de Alimentos LTDA"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsMissingLegalNameWithStandardErrorFormat() throws Exception {
        mockMvc.perform(put("/api/v1/company-fiscal-configuration")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cnpj": "12.345.678/0001-90"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsAnonymousFiscalConfigurationWrite() throws Exception {
        mockMvc.perform(put("/api/v1/company-fiscal-configuration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cnpj\":\"12.345.678/0001-90\",\"legalName\":\"Farelo\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        assertThat(companyFiscalConfigurationRepository.count()).isZero();
    }

    @Test
    void rejectsAnonymousAndNonAdminFiscalConfigurationRead() throws Exception {
        mockMvc.perform(get("/api/v1/company-fiscal-configuration"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        for (UserRole role : List.of(UserRole.MANAGER, UserRole.CASHIER)) {
            mockMvc.perform(get("/api/v1/company-fiscal-configuration")
                            .header("Authorization", "Bearer " + tokenFor(role)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @Test
    void rejectsNonAdminFiscalConfigurationWrite() throws Exception {
        for (UserRole role : List.of(UserRole.MANAGER, UserRole.CASHIER)) {
            mockMvc.perform(put("/api/v1/company-fiscal-configuration")
                            .header("Authorization", "Bearer " + tokenFor(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"cnpj\":\"12.345.678/0001-90\",\"legalName\":\"Farelo\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        assertThat(companyFiscalConfigurationRepository.count()).isZero();
    }
}
