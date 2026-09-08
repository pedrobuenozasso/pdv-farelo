package com.farelo.api.cashier.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.audit.AuditLog;
import com.farelo.api.audit.AuditLogRepository;
import com.farelo.api.cashier.CashMovementRepository;
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
 * Integration test for {@code POST /api/v1/cash-sessions/{id}/withdrawals}/
 * {@code .../supplies} and {@code GET .../movements} (FARELO-310), against
 * a real PostgreSQL instance (Testcontainers).
 *
 * <p>Every test clears {@code cash_movement}/{@code cash_session}/{@code
 * shift} first — own dedicated tables, no other domain's FK touches them.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CashMovementControllerIntegrationTests extends AbstractIntegrationTest {

    private static final String PASSWORD = "senha-forte-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CashMovementRepository cashMovementRepository;

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

    @Autowired
    private AuditLogRepository auditLogRepository;

    @AfterEach
    void cleanUp() {
        cashMovementRepository.deleteAll();
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

    private UUID openSession(String token) throws Exception {
        Shift shift = shiftRepository.save(new Shift("TURNO MOV CTRL " + UUID.randomUUID(), "Caixa Dinheiro", "Tesouraria"));
        MvcResult result = mockMvc.perform(post("/api/v1/cash-sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shiftId\": \"%s\"}".formatted(shift.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), CashSessionResponse.class).id();
    }

    private void closeSession(String token, UUID id) throws Exception {
        mockMvc.perform(post("/api/v1/cash-sessions/{id}/close", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closingAmount\": 0}"))
                .andExpect(status().isOk());
    }

    @Test
    void recordsWithdrawalAndPersistsIt() throws Exception {
        User cashier = persistedUser(UserRole.CASHIER);
        String token = tokenFor(cashier);
        UUID sessionId = openSession(token);

        MvcResult result = mockMvc.perform(post("/api/v1/cash-sessions/{id}/withdrawals", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 50.00, "description": "Depósito bancário"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.cashSessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.amount").value(50.00))
                .andExpect(jsonPath("$.description").value("Depósito bancário"))
                .andExpect(jsonPath("$.createdByUserId").value(cashier.getId().toString()))
                .andExpect(jsonPath("$.createdByUserName").value(cashier.getName()))
                .andReturn();

        CashMovementResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), CashMovementResponse.class);
        assertThat(cashMovementRepository.findById(response.id())).isPresent();

        // FARELO-310: auditable via AuditLogService.
        List<AuditLog> auditTrail = auditLogRepository
                .findByEntityTypeAndEntityIdOrderByCreatedAtDesc("CashMovement", response.id());
        assertThat(auditTrail).hasSize(1);
        assertThat(auditTrail.get(0).getAction()).isEqualTo("CASH_WITHDRAWAL_RECORDED");
    }

    @Test
    void recordsSupplyAndPersistsIt() throws Exception {
        User cashier = persistedUser(UserRole.CASHIER);
        String token = tokenFor(cashier);
        UUID sessionId = openSession(token);

        MvcResult result = mockMvc.perform(post("/api/v1/cash-sessions/{id}/supplies", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 100.00}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SUPPLY"))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andReturn();

        CashMovementResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), CashMovementResponse.class);

        List<AuditLog> auditTrail = auditLogRepository
                .findByEntityTypeAndEntityIdOrderByCreatedAtDesc("CashMovement", response.id());
        assertThat(auditTrail).hasSize(1);
        assertThat(auditTrail.get(0).getAction()).isEqualTo("CASH_SUPPLY_RECORDED");
    }

    @Test
    void rejectsWithdrawalOnClosedSession() throws Exception {
        String token = tokenFor(UserRole.CASHIER);
        UUID sessionId = openSession(token);
        closeSession(token, sessionId);

        mockMvc.perform(post("/api/v1/cash-sessions/{id}/withdrawals", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASH_SESSION_NOT_OPEN"));
    }

    @Test
    void returnsNotFoundForUnknownCashSessionId() throws Exception {
        mockMvc.perform(post("/api/v1/cash-sessions/{id}/withdrawals", UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CASH_SESSION_NOT_FOUND"));
    }

    @Test
    void rejectsWithNoAuthorizationHeader() throws Exception {
        String token = tokenFor(UserRole.CASHIER);
        UUID sessionId = openSession(token);

        mockMvc.perform(post("/api/v1/cash-sessions/{id}/withdrawals", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void rejectsWhenCallerRoleIsNotAllowed() throws Exception {
        String token = tokenFor(UserRole.CASHIER);
        UUID sessionId = openSession(token);

        mockMvc.perform(post("/api/v1/cash-sessions/{id}/withdrawals", sessionId)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void rejectsZeroOrNegativeAmountWithStandardErrorFormat() throws Exception {
        String token = tokenFor(UserRole.CASHIER);
        UUID sessionId = openSession(token);

        mockMvc.perform(post("/api/v1/cash-sessions/{id}/withdrawals", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void listsMovementsOldestFirstScopedToSession() throws Exception {
        // Two distinct operators — CashSessionService#open (FARELO-307)
        // allows only one OPEN session per operator, so the "other session"
        // fixture below needs its own token, not a second openSession(token)
        // call reusing the same cashier (that would 409 CASH_SESSION_ALREADY_OPEN).
        String token = tokenFor(UserRole.CASHIER);
        String otherToken = tokenFor(UserRole.CASHIER);
        UUID sessionId = openSession(token);
        UUID otherSessionId = openSession(otherToken);

        mockMvc.perform(post("/api/v1/cash-sessions/{id}/withdrawals", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 15}"))
                .andExpect(status().isCreated());
        Thread.sleep(10);
        mockMvc.perform(post("/api/v1/cash-sessions/{id}/supplies", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 30}"))
                .andExpect(status().isCreated());
        // A movement on a different session must not leak into this list.
        mockMvc.perform(post("/api/v1/cash-sessions/{id}/withdrawals", otherSessionId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 99}"))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/v1/cash-sessions/{id}/movements", sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn();

        List<CashMovementResponse> movements = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, CashMovementResponse.class));

        assertThat(movements).extracting(CashMovementResponse::amount)
                .extracting(java.math.BigDecimal::intValue)
                .containsExactly(15, 30);
    }

    @Test
    void returnsEmptyListForSessionWithNoMovements() throws Exception {
        String token = tokenFor(UserRole.CASHIER);
        UUID sessionId = openSession(token);

        mockMvc.perform(get("/api/v1/cash-sessions/{id}/movements", sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void rejectsListWithNoAuthorizationHeader() throws Exception {
        String token = tokenFor(UserRole.CASHIER);
        UUID sessionId = openSession(token);

        mockMvc.perform(get("/api/v1/cash-sessions/{id}/movements", sessionId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

}
