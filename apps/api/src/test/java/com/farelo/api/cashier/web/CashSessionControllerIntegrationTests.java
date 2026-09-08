package com.farelo.api.cashier.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.cashier.CashSessionRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code POST}/{@code GET /api/v1/cash-sessions}[/{id}]
 * (FARELO-307), against a real PostgreSQL instance (Testcontainers).
 *
 * <p>Every test clears {@code cash_session}/{@code shift} first (their own
 * dedicated tables, no other domain's FK touches them) — same isolation
 * reasoning as {@code ShiftControllerIntegrationTests}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CashSessionControllerIntegrationTests extends AbstractIntegrationTest {

    private static final String PASSWORD = "senha-forte-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CashSessionRepository cashSessionRepository;

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
        cashSessionRepository.deleteAll();
        shiftRepository.deleteAll();
    }

    private User persistedUser(UserRole role) {
        return userRepository.save(new User(
                "Test User",
                "test-%s@farelo.dev".formatted(UUID.randomUUID()),
                passwordEncoder.encode(PASSWORD),
                role));
    }

    private String tokenFor(User user) {
        return jwtTokenService.issue(user).token();
    }

    private String tokenFor(UserRole role) {
        return tokenFor(persistedUser(role));
    }

    private Shift persistedShift() {
        return shiftRepository.save(new Shift("TURNO CTRL " + UUID.randomUUID(), "Caixa Dinheiro", "Tesouraria"));
    }

    @Test
    void opensCashSessionAndPersistsIt() throws Exception {
        Shift shift = persistedShift();
        User cashier = persistedUser(UserRole.CASHIER);

        MvcResult result = mockMvc.perform(post("/api/v1/cash-sessions")
                        .header("Authorization", "Bearer " + tokenFor(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shiftId": "%s"}
                                """.formatted(shift.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.shiftId").value(shift.getId().toString()))
                .andExpect(jsonPath("$.shiftName").value(shift.getName()))
                .andExpect(jsonPath("$.openedByUserId").value(cashier.getId().toString()))
                .andExpect(jsonPath("$.openedByUserName").value(cashier.getName()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.reconciliationStatus").value("PENDING"))
                .andExpect(jsonPath("$.closingAmount").value(nullValue()))
                .andReturn();

        CashSessionResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), CashSessionResponse.class);

        assertThat(cashSessionRepository.findById(response.id())).isPresent();
    }

    @Test
    void rejectsSecondOpenSessionForSameOperator() throws Exception {
        Shift shift = persistedShift();
        User cashier = persistedUser(UserRole.CASHIER);
        String token = tokenFor(cashier);

        mockMvc.perform(post("/api/v1/cash-sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shiftId\": \"%s\"}".formatted(shift.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/cash-sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shiftId\": \"%s\"}".formatted(shift.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASH_SESSION_ALREADY_OPEN"));
    }

    @Test
    void allowsDifferentOperatorsToOpenAgainstSameShiftConcurrently() throws Exception {
        Shift shift = persistedShift();

        mockMvc.perform(post("/api/v1/cash-sessions")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shiftId\": \"%s\"}".formatted(shift.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/cash-sessions")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shiftId\": \"%s\"}".formatted(shift.getId())))
                .andExpect(status().isCreated());
    }

    @Test
    void returnsNotFoundForUnknownShiftId() throws Exception {
        mockMvc.perform(post("/api/v1/cash-sessions")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shiftId\": \"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHIFT_NOT_FOUND"));
    }

    @Test
    void rejectsOpenWithNoAuthorizationHeader() throws Exception {
        Shift shift = persistedShift();

        mockMvc.perform(post("/api/v1/cash-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shiftId\": \"%s\"}".formatted(shift.getId())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void rejectsOpenWhenCallerRoleIsNotAllowed() throws Exception {
        Shift shift = persistedShift();

        mockMvc.perform(post("/api/v1/cash-sessions")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shiftId\": \"%s\"}".formatted(shift.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void rejectsMissingShiftIdWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/cash-sessions")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void listsCashSessionsNewestFirst() throws Exception {
        Shift shift = persistedShift();
        String tokenA = tokenFor(UserRole.CASHIER);
        String tokenB = tokenFor(UserRole.CASHIER);

        MvcResult firstResult = mockMvc.perform(post("/api/v1/cash-sessions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shiftId\": \"%s\"}".formatted(shift.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        Thread.sleep(10);
        MvcResult secondResult = mockMvc.perform(post("/api/v1/cash-sessions")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shiftId\": \"%s\"}".formatted(shift.getId())))
                .andExpect(status().isCreated())
                .andReturn();

        UUID firstId = objectMapper.readValue(firstResult.getResponse().getContentAsString(), CashSessionResponse.class).id();
        UUID secondId = objectMapper.readValue(secondResult.getResponse().getContentAsString(), CashSessionResponse.class).id();

        MvcResult listResult = mockMvc.perform(get("/api/v1/cash-sessions")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn();

        List<CashSessionResponse> sessions = objectMapper.readValue(
                listResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, CashSessionResponse.class));

        assertThat(sessions).extracting(CashSessionResponse::id).containsExactly(secondId, firstId);
    }

    @Test
    void rejectsListWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/cash-sessions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void getsCashSessionById() throws Exception {
        Shift shift = persistedShift();

        MvcResult openResult = mockMvc.perform(post("/api/v1/cash-sessions")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shiftId\": \"%s\"}".formatted(shift.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = objectMapper.readValue(openResult.getResponse().getContentAsString(), CashSessionResponse.class).id();

        mockMvc.perform(get("/api/v1/cash-sessions/{id}", id)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.shiftName").value(shift.getName()));
    }

    @Test
    void returnsNotFoundForUnknownCashSessionId() throws Exception {
        mockMvc.perform(get("/api/v1/cash-sessions/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CASH_SESSION_NOT_FOUND"));
    }

}
