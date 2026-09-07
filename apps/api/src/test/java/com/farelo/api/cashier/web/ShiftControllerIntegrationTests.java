package com.farelo.api.cashier.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.cashier.Shift;
import com.farelo.api.cashier.ShiftRepository;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.JwtTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code POST}/{@code GET /api/v1/shifts} (FARELO-306),
 * against a real PostgreSQL instance (Testcontainers).
 *
 * <p>Every test clears the {@code shift} table first (its own dedicated
 * table, no FK from any other domain touches it yet) — same isolation
 * reasoning {@code CategoryControllerIntegrationTests} documents for its
 * own {@code @BeforeEach} clean-up.
 *
 * <p>Both endpoints require a role — see {@code ShiftController}'s javadoc
 * for why, unlike {@code CategoryController#list}'s unprotected precedent.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShiftControllerIntegrationTests extends AbstractIntegrationTest {

    private static final String PASSWORD = "senha-forte-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanUp() {
        shiftRepository.deleteAll();
    }

    private String tokenFor(UserRole role) {
        User user = userRepository.save(new User(
                "Test User",
                "test-%s@farelo.dev".formatted(UUID.randomUUID()),
                passwordEncoder.encode(PASSWORD),
                role));
        return jwtTokenService.issue(user).token();
    }

    @Test
    void createsShiftAndPersistsIt() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/shifts")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "TURNO 01", "openingAccountName": "Caixa Dinheiro", "closingAccountName": "Tesouraria"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("TURNO 01"))
                .andExpect(jsonPath("$.openingAccountName").value("Caixa Dinheiro"))
                .andExpect(jsonPath("$.closingAccountName").value("Tesouraria"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        ShiftResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ShiftResponse.class);

        Optional<Shift> persisted = shiftRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getName()).isEqualTo("TURNO 01");
    }

    @Test
    void createsShiftAsManager() throws Exception {
        mockMvc.perform(post("/api/v1/shifts")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "TURNO 02", "openingAccountName": "Caixa Dinheiro", "closingAccountName": "Tesouraria"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsCreateWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/shifts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "TURNO 01", "openingAccountName": "Caixa Dinheiro", "closingAccountName": "Tesouraria"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void rejectsCreateWhenCallerRoleIsNotAllowed() throws Exception {
        mockMvc.perform(post("/api/v1/shifts")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "TURNO 01", "openingAccountName": "Caixa Dinheiro", "closingAccountName": "Tesouraria"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void rejectsDuplicateNameWithConflict() throws Exception {
        shiftRepository.save(new Shift("TURNO 04", "Caixa Dinheiro", "Tesouraria"));

        mockMvc.perform(post("/api/v1/shifts")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "TURNO 04", "openingAccountName": "Outra", "closingAccountName": "Outra"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHIFT_NAME_ALREADY_EXISTS"));
    }

    @Test
    void rejectsBlankFieldsWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/shifts")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsListWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/shifts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void listsShiftsAsCashier() throws Exception {
        shiftRepository.save(new Shift("TURNO B", "Caixa Dinheiro", "Tesouraria"));
        shiftRepository.save(new Shift("TURNO A", "Caixa Dinheiro", "Tesouraria"));

        MvcResult result = mockMvc.perform(get("/api/v1/shifts")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn();

        List<ShiftResponse> shifts = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, ShiftResponse.class));

        assertThat(shifts).extracting(ShiftResponse::name).containsExactly("TURNO A", "TURNO B");
    }

    @Test
    void returnsEmptyListWhenNoShiftsExist() throws Exception {
        mockMvc.perform(get("/api/v1/shifts")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

}
