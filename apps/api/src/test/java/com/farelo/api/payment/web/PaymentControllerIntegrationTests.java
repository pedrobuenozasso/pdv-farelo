package com.farelo.api.payment.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.payment.Payment;
import com.farelo.api.payment.PaymentMethod;
import com.farelo.api.payment.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code GET /api/v1/commands/{number}/payments}
 * (FARELO-140), against a real PostgreSQL instance (Testcontainers).
 *
 * <p>Uses dedicated seeded command numbers (46-47, 49) — distinct from
 * every number already reserved elsewhere in this suite, including {@link
 * com.farelo.api.payment.PaymentRepositoryIntegrationTests}' own 44-45, 48
 * (see that class's javadoc for the fuller registry). {@code
 * COMMAND_WITHOUT_PAYMENTS} (47) is asserted to have zero payments and must
 * never be written to by any test here — the "must not leak into another
 * command's list" fixture below uses a separate number (49) instead of 47,
 * specifically so that assertion stays true regardless of JUnit's
 * (unspecified) method execution order within this class.
 *
 * <p><b>No {@code @RequireRole}</b> — see {@link PaymentController}'s
 * javadoc for why this endpoint stays unprotected at this ticket, same
 * precedent as {@code NotificationControllerIntegrationTests}/{@code
 * AuditLogControllerIntegrationTests}. No token/{@code Authorization}
 * header is sent by any test below, and none is required.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerIntegrationTests extends AbstractIntegrationTest {

    private static final int COMMAND_WITH_PAYMENTS = 46;
    private static final int COMMAND_WITHOUT_PAYMENTS = 47;
    private static final int OTHER_COMMAND_FOR_LEAK_CHECK = 49;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandRepository commandRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private Command command(int number) {
        return commandRepository.findByNumber(number).orElseThrow();
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

}
