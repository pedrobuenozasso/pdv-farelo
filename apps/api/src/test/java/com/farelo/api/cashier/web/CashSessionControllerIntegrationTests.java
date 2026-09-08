package com.farelo.api.cashier.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.cashier.CashSessionRepository;
import com.farelo.api.cashier.Shift;
import com.farelo.api.cashier.ShiftRepository;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.payment.Payment;
import com.farelo.api.payment.PaymentMethod;
import com.farelo.api.payment.PaymentRepository;
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

import java.math.BigDecimal;
import java.util.ArrayList;
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

    @Autowired
    private CommandRepository commandRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    // FARELO-309: Payments/Commands this class creates purely to feed
    // CashSessionService#reconcile's expected-amount calculation — tracked
    // here so cleanUpReconcileFixtures() below deletes exactly these rows,
    // never the shared 1-100 command seed (same "delete only what I made"
    // discipline PaymentControllerIntegrationTests documents).
    private final List<Payment> createdPayments = new ArrayList<>();
    private final List<Command> createdCommands = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        cashSessionRepository.deleteAll();
        shiftRepository.deleteAll();
    }

    @AfterEach
    void cleanUpReconcileFixtures() {
        paymentRepository.deleteAll(createdPayments);
        commandRepository.deleteAll(createdCommands);
        createdPayments.clear();
        createdCommands.clear();
    }

    // Business numbers well outside the shared 1-100 seed (FARELO-031) —
    // no CHECK constraint restricts Command.number's range, and using a
    // number far outside that seed guarantees zero collision with any
    // other test class's reserved-number registry.
    private Command createCommand(int number) {
        Command command = commandRepository.save(new Command(number));
        createdCommands.add(command);
        return command;
    }

    private Payment createCashPayment(Command command, BigDecimal amount) {
        Payment payment = paymentRepository.save(new Payment(command, amount, PaymentMethod.CASH));
        createdPayments.add(payment);
        return payment;
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

    // FARELO-308 -- POST .../close below.

    private UUID openSession(String token, Shift shift) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/cash-sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shiftId\": \"%s\"}".formatted(shift.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), CashSessionResponse.class).id();
    }

    @Test
    void closesOpenSessionAndPersistsClosingFields() throws Exception {
        Shift shift = persistedShift();
        User cashier = persistedUser(UserRole.CASHIER);
        UUID id = openSession(tokenFor(cashier), shift);

        mockMvc.perform(post("/api/v1/cash-sessions/{id}/close", id)
                        .header("Authorization", "Bearer " + tokenFor(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"closingAmount": 250.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closingAmount").value(250.00))
                .andExpect(jsonPath("$.closedAt").exists())
                .andExpect(jsonPath("$.closedByUserId").value(cashier.getId().toString()))
                .andExpect(jsonPath("$.reconciliationStatus").value("PENDING"));

        assertThat(cashSessionRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(com.farelo.api.cashier.CashSessionStatus.CLOSED);
    }

    @Test
    void closesWithZeroClosingAmount() throws Exception {
        Shift shift = persistedShift();
        UUID id = openSession(tokenFor(UserRole.CASHIER), shift);

        mockMvc.perform(post("/api/v1/cash-sessions/{id}/close", id)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"closingAmount": 0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closingAmount").value(0));
    }

    @Test
    void rejectsClosingAlreadyClosedSession() throws Exception {
        Shift shift = persistedShift();
        String token = tokenFor(UserRole.CASHIER);
        UUID id = openSession(token, shift);

        mockMvc.perform(post("/api/v1/cash-sessions/{id}/close", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closingAmount\": 100}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/cash-sessions/{id}/close", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closingAmount\": 100}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASH_SESSION_NOT_OPEN"));
    }

    @Test
    void returnsNotFoundClosingUnknownCashSessionId() throws Exception {
        mockMvc.perform(post("/api/v1/cash-sessions/{id}/close", UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closingAmount\": 100}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CASH_SESSION_NOT_FOUND"));
    }

    @Test
    void rejectsCloseWithNoAuthorizationHeader() throws Exception {
        Shift shift = persistedShift();
        UUID id = openSession(tokenFor(UserRole.CASHIER), shift);

        mockMvc.perform(post("/api/v1/cash-sessions/{id}/close", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closingAmount\": 100}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void rejectsCloseWhenCallerRoleIsNotAllowed() throws Exception {
        Shift shift = persistedShift();
        UUID id = openSession(tokenFor(UserRole.CASHIER), shift);

        mockMvc.perform(post("/api/v1/cash-sessions/{id}/close", id)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closingAmount\": 100}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void rejectsNegativeClosingAmountWithStandardErrorFormat() throws Exception {
        Shift shift = persistedShift();
        UUID id = openSession(tokenFor(UserRole.CASHIER), shift);

        mockMvc.perform(post("/api/v1/cash-sessions/{id}/close", id)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closingAmount\": -10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsMissingClosingAmountWithStandardErrorFormat() throws Exception {
        Shift shift = persistedShift();
        UUID id = openSession(tokenFor(UserRole.CASHIER), shift);

        mockMvc.perform(post("/api/v1/cash-sessions/{id}/close", id)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // FARELO-309 — POST .../reconcile below.

    private UUID openAndCloseSession(String token, Shift shift, BigDecimal closingAmount) throws Exception {
        UUID id = openSession(token, shift);
        mockMvc.perform(post("/api/v1/cash-sessions/{id}/close", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closingAmount\": %s}".formatted(closingAmount)))
                .andExpect(status().isOk());
        return id;
    }

    @Test
    void reconcilesAsMatchedWhenClosingAmountEqualsCashPayments() throws Exception {
        Shift shift = persistedShift();
        String cashierToken = tokenFor(UserRole.CASHIER);
        Command command = createCommand(900001);

        UUID id = openSession(cashierToken, shift);
        createCashPayment(command, new BigDecimal("30.00"));
        createCashPayment(command, new BigDecimal("20.00"));
        mockMvc.perform(post("/api/v1/cash-sessions/{id}/close", id)
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closingAmount\": 50.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/cash-sessions/{id}/reconcile", id)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconciliationStatus").value("MATCHED"))
                .andExpect(jsonPath("$.expectedAmount").value(50.00))
                .andExpect(jsonPath("$.reconciliationDifference").value(0))
                .andExpect(jsonPath("$.reconciledAt").exists())
                .andExpect(jsonPath("$.reconciledByUserId").exists());
    }

    @Test
    void reconcilesAsMismatchedWhenClosingAmountDiffersFromCashPayments() throws Exception {
        Shift shift = persistedShift();
        String cashierToken = tokenFor(UserRole.CASHIER);
        Command command = createCommand(900002);

        UUID id = openSession(cashierToken, shift);
        createCashPayment(command, new BigDecimal("40.00"));
        mockMvc.perform(post("/api/v1/cash-sessions/{id}/close", id)
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closingAmount\": 55.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/cash-sessions/{id}/reconcile", id)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconciliationStatus").value("MISMATCHED"))
                .andExpect(jsonPath("$.expectedAmount").value(40.00))
                .andExpect(jsonPath("$.reconciliationDifference").value(15.00));
    }

    // Non-CASH payments must never enter the expected-amount sum.
    @Test
    void ignoresNonCashPaymentsWhenReconciling() throws Exception {
        Shift shift = persistedShift();
        String cashierToken = tokenFor(UserRole.CASHIER);
        Command command = createCommand(900003);

        UUID id = openSession(cashierToken, shift);
        createCashPayment(command, new BigDecimal("10.00"));
        createdPayments.add(paymentRepository.save(new Payment(command, new BigDecimal("500.00"), PaymentMethod.PIX)));
        mockMvc.perform(post("/api/v1/cash-sessions/{id}/close", id)
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closingAmount\": 10.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/cash-sessions/{id}/reconcile", id)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconciliationStatus").value("MATCHED"))
                .andExpect(jsonPath("$.expectedAmount").value(10.00));
    }

    @Test
    void rejectsReconcilingStillOpenSession() throws Exception {
        Shift shift = persistedShift();
        UUID id = openSession(tokenFor(UserRole.CASHIER), shift);

        mockMvc.perform(post("/api/v1/cash-sessions/{id}/reconcile", id)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASH_SESSION_NOT_CLOSED"));
    }

    @Test
    void rejectsReconcilingAlreadyReconciledSession() throws Exception {
        Shift shift = persistedShift();
        UUID id = openAndCloseSession(tokenFor(UserRole.CASHIER), shift, BigDecimal.ZERO);
        String adminToken = tokenFor(UserRole.ADMIN);

        mockMvc.perform(post("/api/v1/cash-sessions/{id}/reconcile", id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/cash-sessions/{id}/reconcile", id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASH_SESSION_ALREADY_RECONCILED"));
    }

    @Test
    void returnsNotFoundReconcilingUnknownCashSessionId() throws Exception {
        mockMvc.perform(post("/api/v1/cash-sessions/{id}/reconcile", UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CASH_SESSION_NOT_FOUND"));
    }

    @Test
    void rejectsReconcileWithNoAuthorizationHeader() throws Exception {
        Shift shift = persistedShift();
        UUID id = openAndCloseSession(tokenFor(UserRole.CASHIER), shift, BigDecimal.ZERO);

        mockMvc.perform(post("/api/v1/cash-sessions/{id}/reconcile", id))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // Segregation of duties: CASHIER is forbidden, unlike open()/close().
    @Test
    void rejectsReconcileForCashierRole() throws Exception {
        Shift shift = persistedShift();
        String cashierToken = tokenFor(UserRole.CASHIER);
        UUID id = openAndCloseSession(cashierToken, shift, BigDecimal.ZERO);

        mockMvc.perform(post("/api/v1/cash-sessions/{id}/reconcile", id)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

}
