package com.farelo.api.payment.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.audit.AuditLogRepository;
import com.farelo.api.cashier.CashSession;
import com.farelo.api.cashier.CashSessionRepository;
import com.farelo.api.cashier.Shift;
import com.farelo.api.cashier.ShiftRepository;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.command.CommandService;
import com.farelo.api.command.CommandStatus;
import com.farelo.api.payment.Payment;
import com.farelo.api.payment.PaymentMethod;
import com.farelo.api.payment.PaymentRepository;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.JwtTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentRecordIdempotencyIntegrationTests extends AbstractIntegrationTest {
    private static final AtomicInteger NEXT_NUMBER = new AtomicInteger(710);

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired CommandRepository commandRepository;
    @Autowired CommandService commandService;
    @Autowired PaymentRepository paymentRepository;
    @Autowired CashSessionRepository cashSessionRepository;
    @Autowired ShiftRepository shiftRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtTokenService jwtTokenService;

    private final List<UUID> keys = new ArrayList<>();
    private int commandNumber;
    private Command command;
    private Shift shift;
    private CashSession session;
    private String token;

    @BeforeEach
    void setUp() {
        commandNumber = NEXT_NUMBER.getAndIncrement();
        command = new Command(commandNumber);
        command.setStatus(CommandStatus.OPEN);
        command = commandRepository.save(command);

        User user = userRepository.save(new User("Cashier", "payment-idem-" + UUID.randomUUID() + "@farelo.dev",
                passwordEncoder.encode("test-password"), UserRole.CASHIER));
        token = jwtTokenService.issue(user).token();
        shift = shiftRepository.save(new Shift("Payment idem " + UUID.randomUUID(), "Caixa", "Tesouraria"));
        session = cashSessionRepository.save(new CashSession(shift, user.getId(), user.getName(), BigDecimal.ZERO));
    }

    @AfterEach
    void cleanup() {
        for (UUID key : keys) {
            jdbcTemplate.update("DELETE FROM payment_record_idempotency WHERE idempotency_key = ?", key);
        }
        paymentRepository.deleteAll(paymentRepository.findByCashSession(session));
        cashSessionRepository.delete(session);
        shiftRepository.delete(shift);
        commandRepository.findByNumberOrderByCycleNumberDesc(commandNumber)
                .forEach(visit -> commandRepository.deleteById(visit.getId()));
    }

    private String key() {
        UUID key = UUID.randomUUID();
        keys.add(key);
        return key.toString();
    }

    private String body(String amount, String method, String amountReceived) {
        return """
                {"amount": %s, "method": "%s", "cashSessionId": "%s"%s}
                """.formatted(amount, method, session.getId(),
                amountReceived == null ? "" : ", \"amountReceived\": " + amountReceived);
    }

    private String pixWithEvidence(String authorizationCode) {
        return """
                {"amount": 10.00, "method": "PIX", "cashSessionId": "%s",
                 "fiscalDetails": {"integrationType": "2", "authorizationCode": "%s"}}
                """.formatted(session.getId(), authorizationCode);
    }

    private MvcResult submit(String body, String key) throws Exception {
        return mockMvc.perform(post("/api/v1/commands/{number}/payments", commandNumber)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated()).andReturn();
    }

    private List<Payment> payments() {
        return paymentRepository.findByCommandOrderByCreatedAtAsc(command);
    }

    @Test
    void retryReturnsOriginalPaymentAndChangeWithOneLedgerAndAuditRow() throws Exception {
        String key = key();
        String body = body("25.50", "CASH", "30.00");
        MvcResult first = submit(body, key);
        MvcResult second = submit(body, key);

        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        assertThat(second.getResponse().getHeader("Location")).isEqualTo(first.getResponse().getHeader("Location"));
        assertThat(first.getResponse().getHeader("Location"))
                .isEqualTo("/api/v1/commands/" + commandNumber + "/payments/" + payments().get(0).getId());
        assertThat(payments()).hasSize(1);
        Payment payment = payments().get(0);
        assertThat(auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("Payment", payment.getId()))
                .hasSize(1);
        assertThat(paymentRepository.sumAmountByCashSessionAndMethod(session, PaymentMethod.CASH))
                .isEqualByComparingTo("25.50");
    }

    @Test
    void concurrentRetriesCreateOnePayment() throws Exception {
        String key = key();
        String body = body("10.00", "PIX", null);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<MvcResult> call = () -> {
                ready.countDown();
                start.await();
                return submit(body, key);
            };
            Future<MvcResult> one = pool.submit(call);
            Future<MvcResult> two = pool.submit(call);
            ready.await();
            start.countDown();
            assertThat(two.get().getResponse().getContentAsString())
                    .isEqualTo(one.get().getResponse().getContentAsString());
            assertThat(payments()).hasSize(1);
        }
    }

    @Test
    void changedAmountWithSameKeyIsConflict() throws Exception {
        String key = key();
        submit(body("10.00", "PIX", null), key);

        mockMvc.perform(post("/api/v1/commands/{number}/payments", commandNumber)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("20.00", "PIX", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(payments()).hasSize(1);
    }

    @Test
    void persistsFiscalEvidenceAndIncludesItInIdempotencyFingerprint() throws Exception {
        String key = key();
        String body = pixWithEvidence("PIX-RECEIPT-1");
        MvcResult first = submit(body, key);
        submit(body, key);

        assertThat(first.getResponse().getContentAsString()).contains("\"authorizationCode\":\"PIX-RECEIPT-1\"");
        assertThat(payments()).hasSize(1);
        assertThat(payments().get(0).getFiscalDetails().integrationType()).isEqualTo("2");
        assertThat(payments().get(0).getFiscalDetails().authorizationCode()).isEqualTo("PIX-RECEIPT-1");
        mockMvc.perform(post("/api/v1/commands/{number}/payments", commandNumber)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pixWithEvidence("PIX-RECEIPT-2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(payments()).hasSize(1);
    }

    @Test
    void rejectsElectronicEvidenceOnCashPayment() throws Exception {
        String key = key();
        String body = pixWithEvidence("PIX-RECEIPT-1").replace("\"PIX\"", "\"CASH\"");
        mockMvc.perform(post("/api/v1/commands/{number}/payments", commandNumber)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        assertThat(payments()).isEmpty();
    }

    @Test
    void rejectsIntegratedPaymentWithoutInstitutionAndAuthorization() throws Exception {
        String key = key();
        String body = pixWithEvidence("PIX-RECEIPT-1")
                .replace("\"integrationType\": \"2\"", "\"integrationType\": \"1\"");
        mockMvc.perform(post("/api/v1/commands/{number}/payments", commandNumber)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        assertThat(payments()).isEmpty();
    }

    @Test
    void anotherOperatorCannotReplayTheFirstOperatorsPayment() throws Exception {
        String key = key();
        String body = body("10.00", "PIX", null);
        submit(body, key);
        User other = userRepository.save(new User("Other Cashier",
                "other-payment-idem-" + UUID.randomUUID() + "@farelo.dev",
                passwordEncoder.encode("test-password"), UserRole.CASHIER));

        mockMvc.perform(post("/api/v1/commands/{number}/payments", commandNumber)
                        .header("Authorization", "Bearer " + jwtTokenService.issue(other).token())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(payments()).hasSize(1);
    }

    @Test
    void retryAfterReopeningNumberCannotReturnPreviousVisitsPayment() throws Exception {
        String key = key();
        String body = body("10.00", "PIX", null);
        submit(body, key);
        command.setStatus(CommandStatus.CLOSED);
        command = commandRepository.save(command);
        commandService.open(commandNumber);

        mockMvc.perform(post("/api/v1/commands/{number}/payments", commandNumber)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(payments()).hasSize(1);
    }

    @Test
    void equivalentMoneyFormatsReplaySamePayment() throws Exception {
        String key = key();
        MvcResult first = submit(body("10.0", "CASH", "20.00"), key);
        MvcResult second = submit(body("10.00", "CASH", "20.0"), key);
        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        assertThat(payments()).hasSize(1);
    }

    @Test
    void failedPaymentRollsBackKeyForRetry() throws Exception {
        command.setStatus(CommandStatus.CLOSED);
        command = commandRepository.save(command);
        String key = key();
        String body = body("10.00", "PIX", null);
        mockMvc.perform(post("/api/v1/commands/{number}/payments", commandNumber)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMAND_CANNOT_ACCEPT_PAYMENTS"));

        command.setStatus(CommandStatus.OPEN);
        command = commandRepository.save(command);
        submit(body, key);
        assertThat(payments()).hasSize(1);
    }

    @Test
    void invalidKeyIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/commands/{number}/payments", commandNumber)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("10.00", "PIX", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));
        assertThat(payments()).isEmpty();
    }
}
