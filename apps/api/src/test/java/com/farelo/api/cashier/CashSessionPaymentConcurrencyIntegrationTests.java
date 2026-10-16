package com.farelo.api.cashier;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.command.CommandStatus;
import com.farelo.api.payment.PaymentMethod;
import com.farelo.api.payment.PaymentRepository;
import com.farelo.api.payment.PaymentService;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CashSessionPaymentConcurrencyIntegrationTests extends AbstractIntegrationTest {

    private static final int COMMAND_NUMBER = 900021;

    @Autowired private CashSessionRepository cashSessionRepository;
    @Autowired private ShiftRepository shiftRepository;
    @Autowired private CommandRepository commandRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentService paymentService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void paymentWaitingBehindCloseLockObservesClosedSessionAndIsRejected() throws Exception {
        User operator = userRepository.save(new User(
                "Caixa Concorrência", "cash-race-%s@farelo.dev".formatted(UUID.randomUUID()),
                passwordEncoder.encode("test-password"), UserRole.CASHIER));
        Shift shift = shiftRepository.save(new Shift(
                "TURNO RACE " + UUID.randomUUID(), "Caixa Dinheiro", "Tesouraria"));
        CashSession session = cashSessionRepository.save(new CashSession(
                shift, operator.getId(), operator.getName(), BigDecimal.ZERO));
        Command command = new Command(COMMAND_NUMBER);
        command.setStatus(CommandStatus.OPEN);
        command = commandRepository.save(command);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch paymentStarted = new CountDownLatch(1);
        try {
            Future<?> pending = transactionTemplate.execute(status -> {
                CashSession locked = cashSessionRepository.findByIdForUpdate(session.getId()).orElseThrow();
                Future<?> payment = executor.submit(() -> {
                    String observedStatus = jdbcTemplate.queryForObject(
                            "SELECT status FROM cash_session WHERE id = ?", String.class, session.getId());
                    assertThat(observedStatus).isEqualTo("OPEN");
                    paymentStarted.countDown();
                    paymentService.record(COMMAND_NUMBER, BigDecimal.ONE, PaymentMethod.CASH,
                            session.getId(), operator.getId(), UserRole.CASHIER);
                });
                try {
                    assertThat(paymentStarted.await(5, TimeUnit.SECONDS)).isTrue();
                    // Hold the close transaction while the payment proceeds.
                    // A payment that ignores the row lock can finish here.
                    Thread.sleep(500);
                    assertThat(payment).isNotDone();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                locked.setStatus(CashSessionStatus.CLOSED);
                locked.setClosingAmount(BigDecimal.ZERO);
                locked.setClosedAt(OffsetDateTime.now(ZoneOffset.UTC));
                locked.setClosedByUserId(operator.getId());
                locked.setClosedByUserName(operator.getName());
                cashSessionRepository.save(locked);
                return payment;
            });

            try {
                pending.get(10, TimeUnit.SECONDS);
                throw new AssertionError("Pagamento após fechamento deveria falhar");
            } catch (ExecutionException ex) {
                assertThat(ex.getCause()).isInstanceOf(CashSessionNotOpenException.class);
            }
            assertThat(paymentRepository.findByCashSession(session)).isEmpty();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            jdbcTemplate.update("DELETE FROM payment WHERE cash_session_id = ?", session.getId());
            cashSessionRepository.deleteById(session.getId());
            shiftRepository.deleteById(shift.getId());
            commandRepository.deleteById(command.getId());
        }
    }

}
