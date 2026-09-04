package com.farelo.api.payment.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.command.CommandStatus;
import com.farelo.api.payment.Payment;
import com.farelo.api.payment.PaymentMethod;
import com.farelo.api.payment.PaymentRepository;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.JwtTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code GET}/{@code POST
 * /api/v1/commands/{number}/payments} (FARELO-140/141) and {@code GET
 * /api/v1/commands/{number}/payments/total} (FARELO-142), against a real
 * PostgreSQL instance (Testcontainers).
 *
 * <p>Uses dedicated seeded command numbers (46-47, 49) for the {@code GET
 * .../payments} tests, 60-66 for the {@code POST} ({@link #record}) tests,
 * and 70-72 for the new {@code GET .../payments/total} ({@link #totalPaid})
 * tests below — distinct from every number already reserved elsewhere in
 * this suite, including {@link com.farelo.api.payment.PaymentRepositoryIntegrationTests}'
 * own 44-45, 48, 67-69 (see that class's javadoc for the fuller registry)
 * and, critically, {@code CommandSeedIntegrationTests}' sampled numbers (1,
 * 50, 100 — asserted to stay {@code AVAILABLE} forever; 50 was the first
 * choice here and had to move once that collision surfaced as a test
 * failure). Each mutated number is reset back to {@code AVAILABLE} in {@code
 * resetMutatedTestCommands()} below, same {@code @AfterEach} pattern {@code
 * CommandControllerIntegrationTests} uses, since these commands are shared
 * state across this whole suite via the singleton Postgres container (see
 * {@link AbstractIntegrationTest}). {@code COMMAND_WITHOUT_PAYMENTS} (47) is
 * asserted to have zero payments and must never be written to by any test
 * here — the "must not leak into another command's list" fixture below uses
 * a separate number (49) instead of 47, specifically so that assertion stays
 * true regardless of JUnit's (unspecified) method execution order within
 * this class. Each of 60-66 and 70-72 is used by exactly one test method
 * (never shared), for the same order-independence reason.
 *
 * <p><b>{@link #listByCommand} and {@link #totalPaid} stay unprotected</b> —
 * see {@link PaymentController}'s javadoc for why, same precedent as {@code
 * NotificationControllerIntegrationTests}/{@code
 * AuditLogControllerIntegrationTests}. No token/{@code Authorization} header
 * is sent by any {@code GET} test below, and none is required.
 *
 * <p><b>{@link #record} requires a token</b> (FARELO-141: {@code
 * ADMIN}/{@code MANAGER}/{@code CASHIER}) — every {@code POST} test below
 * mints one via {@link #tokenFor}, same {@code tokenFor(UserRole)} pattern
 * {@code CommandControllerIntegrationTests} established for FARELO-124.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerIntegrationTests extends AbstractIntegrationTest {

    private static final int COMMAND_WITH_PAYMENTS = 46;
    private static final int COMMAND_WITHOUT_PAYMENTS = 47;
    private static final int OTHER_COMMAND_FOR_LEAK_CHECK = 49;

    // FARELO-141 (POST .../payments) — see class javadoc.
    private static final int RECORD_OPEN_NUMBER = 60;
    private static final int RECORD_PAYMENT_REQUESTED_NUMBER = 61;
    private static final int RECORD_AVAILABLE_NUMBER = 62;
    private static final int RECORD_CLOSED_NUMBER = 63;
    private static final int RECORD_VALIDATION_NUMBER = 64;
    private static final int RECORD_RBAC_NUMBER = 65;
    private static final int RECORD_BLOCKED_NUMBER = 66;

    // FARELO-142 (GET .../payments/total) — see class javadoc.
    private static final int TOTAL_COMMAND_WITH_PAYMENTS = 70;
    private static final int TOTAL_OTHER_COMMAND_FOR_LEAK_CHECK = 71;
    private static final int TOTAL_COMMAND_WITHOUT_PAYMENTS = 72;

    private static final String PASSWORD = "senha-forte-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandRepository commandRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    private Command command(int number) {
        return commandRepository.findByNumber(number).orElseThrow();
    }

    // FARELO-141: the record()-related tests mutate command status on 60-66
    // (see class javadoc for why that specific range) — reset them back to
    // the seed default after every test, same @AfterEach pattern
    // CommandControllerIntegrationTests uses, so no other test in this suite
    // (e.g. CommandSeedIntegrationTests) ever observes a leftover non-
    // AVAILABLE status on one of these numbers.
    @AfterEach
    void resetMutatedTestCommands() {
        resetToAvailable(RECORD_OPEN_NUMBER);
        resetToAvailable(RECORD_PAYMENT_REQUESTED_NUMBER);
        resetToAvailable(RECORD_AVAILABLE_NUMBER);
        resetToAvailable(RECORD_CLOSED_NUMBER);
        resetToAvailable(RECORD_VALIDATION_NUMBER);
        resetToAvailable(RECORD_RBAC_NUMBER);
        resetToAvailable(RECORD_BLOCKED_NUMBER);
    }

    private void resetToAvailable(int number) {
        Command command = command(number);
        command.setStatus(CommandStatus.AVAILABLE);
        commandRepository.save(command);
    }

    // Test-only setup: sets a command straight to a given status, bypassing
    // the API — same helper CommandControllerIntegrationTests uses to arrange
    // scenarios before exercising a status-dependent endpoint.
    private void setStatus(int number, CommandStatus status) {
        Command command = command(number);
        command.setStatus(status);
        commandRepository.save(command);
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
    void returnsEmptyListWhenCommandHasNoPayments() throws Exception {
        mockMvc.perform(get("/api/v1/commands/{number}/payments", COMMAND_WITHOUT_PAYMENTS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void returnsNotFoundForUnknownCommandNumber() throws Exception {
        mockMvc.perform(get("/api/v1/commands/{number}/payments", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMAND_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void listsPaymentsOldestFirstScopedToCommand() throws Exception {
        Command command = command(COMMAND_WITH_PAYMENTS);
        Command otherCommand = command(OTHER_COMMAND_FOR_LEAK_CHECK);

        Payment first = paymentRepository.save(new Payment(command, new BigDecimal("12.00"), PaymentMethod.CASH));
        // Distinct, increasing createdAt for a deterministic order
        // assertion — same pattern already used throughout this suite
        // (e.g. PrintJobControllerIntegrationTests' ordering test).
        Thread.sleep(10);
        Payment second = paymentRepository.save(
                new Payment(command, new BigDecimal("8.75"), PaymentMethod.PIX));
        // A payment on a different command must not leak into this list.
        paymentRepository.save(new Payment(otherCommand, new BigDecimal("50.00"), PaymentMethod.OTHER));

        mockMvc.perform(get("/api/v1/commands/{number}/payments", COMMAND_WITH_PAYMENTS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(first.getId().toString()))
                .andExpect(jsonPath("$[0].commandNumber").value(COMMAND_WITH_PAYMENTS))
                .andExpect(jsonPath("$[0].amount").value(12.00))
                .andExpect(jsonPath("$[0].method").value("CASH"))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[1].id").value(second.getId().toString()))
                .andExpect(jsonPath("$[1].amount").value(8.75))
                .andExpect(jsonPath("$[1].method").value("PIX"));
    }

    // --- FARELO-141: POST .../payments -------------------------------------

    @Test
    void recordsPaymentWhenCommandIsOpen() throws Exception {
        setStatus(RECORD_OPEN_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_OPEN_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 25.50, "method": "PIX"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.commandNumber").value(RECORD_OPEN_NUMBER))
                .andExpect(jsonPath("$.amount").value(25.50))
                .andExpect(jsonPath("$.method").value("PIX"))
                .andExpect(jsonPath("$.createdAt").exists());

        List<Payment> persisted = paymentRepository.findByCommandOrderByCreatedAtAsc(command(RECORD_OPEN_NUMBER));
        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).getAmount()).isEqualByComparingTo("25.50");
        assertThat(persisted.get(0).getMethod()).isEqualTo(PaymentMethod.PIX);
    }

    @Test
    void recordsPaymentWhenCommandIsPaymentRequested() throws Exception {
        setStatus(RECORD_PAYMENT_REQUESTED_NUMBER, CommandStatus.PAYMENT_REQUESTED);

        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_PAYMENT_REQUESTED_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 40.00, "method": "CASH"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.commandNumber").value(RECORD_PAYMENT_REQUESTED_NUMBER))
                .andExpect(jsonPath("$.amount").value(40.00))
                .andExpect(jsonPath("$.method").value("CASH"));

        assertThat(paymentRepository.findByCommandOrderByCreatedAtAsc(command(RECORD_PAYMENT_REQUESTED_NUMBER)))
                .hasSize(1);
    }

    @Test
    void rejectsPaymentWhenCommandIsAvailable() throws Exception {
        // RECORD_AVAILABLE_NUMBER is left at its seed default (AVAILABLE) —
        // never opened, so there's nothing to pay for.
        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_AVAILABLE_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10.00, "method": "CASH"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMAND_CANNOT_ACCEPT_PAYMENTS"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());

        assertThat(paymentRepository.findByCommandOrderByCreatedAtAsc(command(RECORD_AVAILABLE_NUMBER))).isEmpty();
    }

    @Test
    void rejectsPaymentWhenCommandIsClosed() throws Exception {
        setStatus(RECORD_CLOSED_NUMBER, CommandStatus.CLOSED);

        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_CLOSED_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10.00, "method": "CASH"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMAND_CANNOT_ACCEPT_PAYMENTS"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsPaymentWhenCommandIsBlocked() throws Exception {
        setStatus(RECORD_BLOCKED_NUMBER, CommandStatus.BLOCKED);

        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_BLOCKED_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10.00, "method": "CASH"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMAND_CANNOT_ACCEPT_PAYMENTS"));
    }

    @Test
    void returnsCommandNotFoundWhenRecordingPaymentForUnknownNumber() throws Exception {
        mockMvc.perform(post("/api/v1/commands/{number}/payments", 999)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10.00, "method": "CASH"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMAND_NOT_FOUND"));
    }

    @Test
    void rejectsNonPositiveAmountWithStandardErrorFormat() throws Exception {
        setStatus(RECORD_VALIDATION_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_VALIDATION_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 0, "method": "CASH"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());

        assertThat(paymentRepository.findByCommandOrderByCreatedAtAsc(command(RECORD_VALIDATION_NUMBER))).isEmpty();
    }

    @Test
    void rejectsNegativeAmountWithStandardErrorFormat() throws Exception {
        setStatus(RECORD_VALIDATION_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_VALIDATION_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": -5.00, "method": "CASH"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsMissingMethodWithStandardErrorFormat() throws Exception {
        setStatus(RECORD_VALIDATION_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_VALIDATION_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // --- FARELO-141: RBAC on record() ---------------------------------------

    @Test
    void rejectsRecordWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_RBAC_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10.00, "method": "CASH"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // ATTENDANT has no business recording payments — see PaymentController's
    // javadoc (cash-handling action, same exclusion CommandController#close
    // already makes).
    @Test
    void rejectsRecordWhenCallerRoleIsNotAllowed() throws Exception {
        setStatus(RECORD_RBAC_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_RBAC_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10.00, "method": "CASH"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlationId").exists());

        assertThat(paymentRepository.findByCommandOrderByCreatedAtAsc(command(RECORD_RBAC_NUMBER))).isEmpty();
    }

    @Test
    void allowsRecordAsCashier() throws Exception {
        setStatus(RECORD_RBAC_NUMBER, CommandStatus.OPEN);

        mockMvc.perform(post("/api/v1/commands/{number}/payments", RECORD_RBAC_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10.00, "method": "CASH"}
                                """))
                .andExpect(status().isCreated());
    }

    // --- FARELO-142: GET .../payments/total ---------------------------------

    @Test
    void totalPaidReturnsZeroWhenCommandHasNoPayments() throws Exception {
        mockMvc.perform(get("/api/v1/commands/{number}/payments/total", TOTAL_COMMAND_WITHOUT_PAYMENTS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commandNumber").value(TOTAL_COMMAND_WITHOUT_PAYMENTS))
                .andExpect(jsonPath("$.totalPaid").value(0));
    }

    @Test
    void totalPaidSumsMultiplePaymentsScopedToCommand() throws Exception {
        Command command = command(TOTAL_COMMAND_WITH_PAYMENTS);
        Command otherCommand = command(TOTAL_OTHER_COMMAND_FOR_LEAK_CHECK);

        paymentRepository.save(new Payment(command, new BigDecimal("12.00"), PaymentMethod.CASH));
        paymentRepository.save(new Payment(command, new BigDecimal("8.75"), PaymentMethod.PIX));
        // A payment on a different command must not leak into this total.
        paymentRepository.save(new Payment(otherCommand, new BigDecimal("500.00"), PaymentMethod.OTHER));

        mockMvc.perform(get("/api/v1/commands/{number}/payments/total", TOTAL_COMMAND_WITH_PAYMENTS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commandNumber").value(TOTAL_COMMAND_WITH_PAYMENTS))
                .andExpect(jsonPath("$.totalPaid").value(20.75));
    }

    @Test
    void totalPaidReturnsNotFoundForUnknownCommandNumber() throws Exception {
        mockMvc.perform(get("/api/v1/commands/{number}/payments/total", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMAND_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

}
