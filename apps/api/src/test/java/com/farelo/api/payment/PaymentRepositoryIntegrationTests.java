package com.farelo.api.payment;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link Payment} maps correctly onto the table created by
 * {@code V26__create_payment_table.sql}, against a real PostgreSQL
 * instance — including that it round-trips against a real {@link Command},
 * that {@code findByCommandOrderByCreatedAtAsc} returns rows oldest-first,
 * scoped to the right command, and (FARELO-142) that {@code
 * sumAmountByCommand} sums correctly and stays scoped to the right command.
 *
 * <p>Uses dedicated seeded command numbers (44-45, 48, and 67-69 for
 * FARELO-142's {@code sumAmountByCommand} tests below) — the next free
 * numbers after every one already reserved elsewhere in this suite (see
 * e.g. {@code InventoryMovementServiceIntegrationTests}: 34; {@code
 * OutboxWorkerOrderReadyIntegrationTests}: 40-43; {@code
 * CommandControllerIntegrationTests}: 1-7, 91-92, 999; {@code
 * CommandRepositoryIntegrationTests}: 101; {@code
 * PaymentControllerIntegrationTests}: 46-47, 49, 60-66, and 70-72 for its
 * own FARELO-142 tests). 48 is dedicated to the "command with no payments"
 * test, separate from {@code OTHER_COMMAND_NUMBER} (45) — that one gets a
 * payment written to it by
 * {@link #findByCommandOrderByCreatedAtAscReturnsOldestFirstScopedToCommand()}
 * as its own "must not leak" fixture, and JUnit gives no ordering guarantee
 * between test methods in this class, so reusing 45 for "must have zero
 * rows" would be flaky depending on execution order. Same reasoning is why
 * the FARELO-142 tests below get their own fresh numbers (67-69) rather
 * than reusing 44/45/48: this class has no per-test cleanup (see below),
 * so 44 already accumulates payments written by {@link #savesAndFindsPayment()}
 * and {@link #savesEveryPaymentMethod()} — an exact-sum assertion against
 * that same command would be order-dependent on whichever of those methods
 * happens to run first.
 *
 * <p>No {@code @BeforeEach} table cleanup — same reasoning as {@code
 * InventoryMovementRepositoryIntegrationTests}: every test scopes its
 * assertions to its own dedicated command, so leftover rows from other test
 * classes sharing the singleton Postgres container (see {@link
 * AbstractIntegrationTest}) never affect an assertion here. No status to
 * mutate/reset either (see {@link Payment}'s javadoc — append-only, no
 * status field), so no {@code @AfterEach} is needed.
 */
@SpringBootTest
class PaymentRepositoryIntegrationTests extends AbstractIntegrationTest {

    private static final int SEEDED_COMMAND_NUMBER = 44;
    private static final int OTHER_COMMAND_NUMBER = 45;
    private static final int EMPTY_COMMAND_NUMBER = 48;

    // FARELO-142 (sumAmountByCommand) — see class javadoc for why these are
    // dedicated, fresh numbers rather than reusing 44/45/48 above.
    private static final int SUM_COMMAND_NUMBER = 67;
    private static final int SUM_OTHER_COMMAND_NUMBER = 68;
    private static final int SUM_EMPTY_COMMAND_NUMBER = 69;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CommandRepository commandRepository;

    private Command command(int number) {
        return commandRepository.findByNumber(number).orElseThrow();
    }

    @Test
    void savesAndFindsPayment() {
        Command command = command(SEEDED_COMMAND_NUMBER);

        Payment saved = paymentRepository.saveAndFlush(
                new Payment(command, new BigDecimal("25.50"), PaymentMethod.PIX));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCommand().getId()).isEqualTo(command.getId());
        assertThat(saved.getMethod()).isEqualTo(PaymentMethod.PIX);

        Optional<Payment> found = paymentRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getAmount()).isEqualByComparingTo("25.50");
        assertThat(found.get().getMethod()).isEqualTo(PaymentMethod.PIX);
    }

    @Test
    void savesEveryPaymentMethod() {
        Command command = command(SEEDED_COMMAND_NUMBER);

        for (PaymentMethod method : PaymentMethod.values()) {
            Payment saved = paymentRepository.saveAndFlush(
                    new Payment(command, new BigDecimal("10.00"), method));

            assertThat(paymentRepository.findById(saved.getId()).orElseThrow().getMethod())
                    .isEqualTo(method);
        }
    }

    @Test
    void findByCommandOrderByCreatedAtAscReturnsOldestFirstScopedToCommand() {
        Command command = command(SEEDED_COMMAND_NUMBER);
        Command otherCommand = command(OTHER_COMMAND_NUMBER);

        Payment first = paymentRepository.saveAndFlush(
                new Payment(command, new BigDecimal("15.00"), PaymentMethod.CASH));
        Payment second = paymentRepository.saveAndFlush(
                new Payment(command, new BigDecimal("30.00"), PaymentMethod.CREDIT_CARD));
        // A payment on a different command must not leak into this list —
        // this is the exact guarantee FARELO-142 ("multiple payments per
        // comanda") will rely on to sum only the right comanda's rows.
        paymentRepository.saveAndFlush(
                new Payment(otherCommand, new BigDecimal("99.00"), PaymentMethod.DEBIT_CARD));

        List<Payment> payments = paymentRepository.findByCommandOrderByCreatedAtAsc(command);

        assertThat(payments).hasSize(2);
        assertThat(payments.get(0).getId()).isEqualTo(first.getId());
        assertThat(payments.get(1).getId()).isEqualTo(second.getId());
    }

    @Test
    void findByCommandOrderByCreatedAtAscReturnsEmptyListForCommandWithNoPayments() {
        Command command = command(EMPTY_COMMAND_NUMBER);

        assertThat(paymentRepository.findByCommandOrderByCreatedAtAsc(command)).isEmpty();
    }

    // --- FARELO-142: sumAmountByCommand -------------------------------------

    @Test
    void sumAmountByCommandSumsMultiplePaymentsWithDifferentMethodsScopedToCommand() {
        Command command = command(SUM_COMMAND_NUMBER);
        Command otherCommand = command(SUM_OTHER_COMMAND_NUMBER);

        paymentRepository.saveAndFlush(new Payment(command, new BigDecimal("12.00"), PaymentMethod.CASH));
        paymentRepository.saveAndFlush(new Payment(command, new BigDecimal("8.75"), PaymentMethod.PIX));
        paymentRepository.saveAndFlush(new Payment(command, new BigDecimal("5.25"), PaymentMethod.CREDIT_CARD));
        // A payment on a different command must not leak into this sum.
        paymentRepository.saveAndFlush(new Payment(otherCommand, new BigDecimal("999.00"), PaymentMethod.OTHER));

        BigDecimal total = paymentRepository.sumAmountByCommand(command);

        assertThat(total).isEqualByComparingTo("26.00");
    }

    @Test
    void sumAmountByCommandReturnsZeroNotNullForCommandWithNoPayments() {
        Command command = command(SUM_EMPTY_COMMAND_NUMBER);

        BigDecimal total = paymentRepository.sumAmountByCommand(command);

        assertThat(total).isNotNull();
        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
    }

}
