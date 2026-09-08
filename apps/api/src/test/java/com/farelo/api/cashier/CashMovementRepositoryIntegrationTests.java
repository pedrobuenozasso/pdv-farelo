package com.farelo.api.cashier;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link CashMovement} maps correctly onto the table created
 * by {@code V41__create_cash_movement_table.sql}, against a real PostgreSQL
 * instance — including that it round-trips against a real {@link
 * CashSession}, the four {@link CashMovementType} values, and that {@code
 * findByCashSessionOrderByCreatedAtAsc} returns rows oldest-first, scoped
 * to the right session.
 */
@SpringBootTest
class CashMovementRepositoryIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private CashMovementRepository cashMovementRepository;

    @Autowired
    private CashSessionRepository cashSessionRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    @AfterEach
    void cleanUp() {
        cashMovementRepository.deleteAll();
        cashSessionRepository.deleteAll();
        shiftRepository.deleteAll();
    }

    private CashSession persistedSession() {
        Shift shift = shiftRepository.save(new Shift("TURNO MOV " + UUID.randomUUID(), "Caixa Dinheiro", "Tesouraria"));
        return cashSessionRepository.save(new CashSession(shift, UUID.randomUUID(), "Maria"));
    }

    @Test
    void savesAndFindsCashMovement() {
        CashSession session = persistedSession();

        CashMovement saved = cashMovementRepository.saveAndFlush(new CashMovement(
                session, CashMovementType.WITHDRAWAL, new BigDecimal("50.00"), "Depósito bancário",
                UUID.randomUUID(), "Maria"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCashSession().getId()).isEqualTo(session.getId());

        Optional<CashMovement> found = cashMovementRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getType()).isEqualTo(CashMovementType.WITHDRAWAL);
        assertThat(found.get().getAmount()).isEqualByComparingTo("50.00");
        assertThat(found.get().getDescription()).isEqualTo("Depósito bancário");
    }

    @Test
    void savesEveryCashMovementType() {
        CashSession session = persistedSession();

        for (CashMovementType type : CashMovementType.values()) {
            CashMovement saved = cashMovementRepository.saveAndFlush(new CashMovement(
                    session, type, new BigDecimal("10.00"), null, UUID.randomUUID(), "Maria"));

            assertThat(cashMovementRepository.findById(saved.getId()).orElseThrow().getType()).isEqualTo(type);
        }
    }

    @Test
    void allowsNullDescription() {
        CashSession session = persistedSession();

        CashMovement saved = cashMovementRepository.saveAndFlush(new CashMovement(
                session, CashMovementType.SUPPLY, new BigDecimal("25.00"), null, UUID.randomUUID(), "Maria"));

        assertThat(cashMovementRepository.findById(saved.getId()).orElseThrow().getDescription()).isNull();
    }

    @Test
    void findByCashSessionOrderByCreatedAtAscReturnsOldestFirstScopedToSession() {
        CashSession session = persistedSession();
        CashSession otherSession = persistedSession();

        CashMovement first = cashMovementRepository.saveAndFlush(new CashMovement(
                session, CashMovementType.WITHDRAWAL, new BigDecimal("15.00"), null, UUID.randomUUID(), "Maria"));
        CashMovement second = cashMovementRepository.saveAndFlush(new CashMovement(
                session, CashMovementType.SUPPLY, new BigDecimal("30.00"), null, UUID.randomUUID(), "Maria"));
        // A movement on a different session must not leak into this list.
        cashMovementRepository.saveAndFlush(new CashMovement(
                otherSession, CashMovementType.EXPENSE, new BigDecimal("99.00"), null, UUID.randomUUID(), "Maria"));

        List<CashMovement> movements = cashMovementRepository.findByCashSessionOrderByCreatedAtAsc(session);

        assertThat(movements).hasSize(2);
        assertThat(movements.get(0).getId()).isEqualTo(first.getId());
        assertThat(movements.get(1).getId()).isEqualTo(second.getId());
    }

    @Test
    void findByCashSessionOrderByCreatedAtAscReturnsEmptyListForSessionWithNoMovements() {
        CashSession session = persistedSession();

        assertThat(cashMovementRepository.findByCashSessionOrderByCreatedAtAsc(session)).isEmpty();
    }

}
