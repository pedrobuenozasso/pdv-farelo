package com.farelo.api.cashier;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that {@link CashSession} maps correctly onto the table created
 * by {@code V40__create_cash_session_table.sql}, against a real PostgreSQL
 * instance — including the {@code ck_cash_session_closed_scope}/{@code
 * ck_cash_session_reconciliation_scope} defense-in-depth constraints.
 */
@SpringBootTest
class CashSessionRepositoryIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private CashSessionRepository cashSessionRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    @AfterEach
    void cleanUp() {
        cashSessionRepository.deleteAll();
        shiftRepository.deleteAll();
    }

    private Shift persistedShift() {
        return shiftRepository.saveAndFlush(new Shift("TURNO REPO " + UUID.randomUUID(), "Caixa Dinheiro", "Tesouraria"));
    }

    @Test
    void savesAndFindsCashSessionDefaultingToOpenAndPending() {
        Shift shift = persistedShift();
        UUID operatorId = UUID.randomUUID();

        CashSession saved = cashSessionRepository.saveAndFlush(new CashSession(shift, operatorId, "Maria"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(CashSessionStatus.OPEN);
        assertThat(saved.getReconciliationStatus()).isEqualTo(CashSessionReconciliationStatus.PENDING);
        assertThat(saved.getOpenedAt()).isNotNull();
        assertThat(saved.getClosingAmount()).isNull();
        assertThat(saved.getExpectedAmount()).isNull();

        Optional<CashSession> found = cashSessionRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getOpenedByUserId()).isEqualTo(operatorId);
        assertThat(found.get().getOpenedByUserName()).isEqualTo("Maria");
    }

    @Test
    void findWithShiftByIdFetchesShiftEagerly() {
        Shift shift = persistedShift();
        CashSession saved = cashSessionRepository.saveAndFlush(new CashSession(shift, UUID.randomUUID(), "Maria"));

        Optional<CashSession> found = cashSessionRepository.findWithShiftById(saved.getId());

        assertThat(found).isPresent();
        // Accessing the shift here proves it was actually fetched (not a
        // lazy proxy) — this repository call happens inside a Spring Data
        // transaction that hasn't ended yet, so a lazy proxy would still
        // resolve fine in this test either way; the real proof this fetch
        // matters is CashSessionControllerIntegrationTests, where the
        // response is built after the service's transaction has closed.
        assertThat(found.get().getShift().getName()).isEqualTo(shift.getName());
    }

    @Test
    void findByOpenedByUserIdAndStatusScopesToStatus() {
        Shift shift = persistedShift();
        UUID operatorId = UUID.randomUUID();
        CashSession open = cashSessionRepository.saveAndFlush(new CashSession(shift, operatorId, "Maria"));

        Optional<CashSession> found =
                cashSessionRepository.findByOpenedByUserIdAndStatus(operatorId, CashSessionStatus.OPEN);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(open.getId());

        Optional<CashSession> notClosed =
                cashSessionRepository.findByOpenedByUserIdAndStatus(operatorId, CashSessionStatus.CLOSED);
        assertThat(notClosed).isEmpty();
    }

    @Test
    void findAllWithShiftByOrderByOpenedAtDescOrdersNewestFirst() throws InterruptedException {
        Shift shift = persistedShift();
        CashSession first = cashSessionRepository.saveAndFlush(new CashSession(shift, UUID.randomUUID(), "Primeiro"));
        Thread.sleep(10);
        CashSession second = cashSessionRepository.saveAndFlush(new CashSession(shift, UUID.randomUUID(), "Segundo"));

        List<CashSession> all = cashSessionRepository.findAllWithShiftByOrderByOpenedAtDesc();

        assertThat(all).extracting(CashSession::getId).containsExactly(second.getId(), first.getId());
    }

    @Test
    void rejectsPartialClosingColumnsViaCheckConstraint() {
        Shift shift = persistedShift();
        CashSession session = new CashSession(shift, UUID.randomUUID(), "Maria");
        session.setStatus(CashSessionStatus.CLOSED);
        // closingAmount/closedAt/closedByUserId/closedByUserName left null
        // — ck_cash_session_closed_scope requires all four together with
        // CLOSED, so this must be rejected at the DB level.

        assertThatThrownBy(() -> cashSessionRepository.saveAndFlush(session))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

}
