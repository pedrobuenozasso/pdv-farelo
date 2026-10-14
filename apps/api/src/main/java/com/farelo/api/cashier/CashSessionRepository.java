package com.farelo.api.cashier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashSessionRepository extends JpaRepository<CashSession, UUID> {

    // FARELO-307: backs CashSessionService#open's "one open session per
    // operator" check. No JOIN FETCH needed — the result is only ever
    // inspected for presence, never returned for response serialization.
    Optional<CashSession> findByOpenedByUserIdAndStatus(UUID openedByUserId, CashSessionStatus status);

    // JOIN FETCH shift for the same reason PaymentRepository#findByCommandOrderByCreatedAtAsc
    // already documents: open-in-view is false (application.yml), and
    // CashSessionResponse#from reads session.getShift().getName() in the
    // controller, after this method's (short) transaction has already
    // closed — without the fetch, `shift` would be an uninitialized lazy
    // proxy and throw LazyInitializationException. Used whenever an
    // *existing* row is loaded from the DB and may be returned for
    // response building (getById/close/reconcile) — not needed for open(),
    // whose CashSession is freshly constructed in-memory with the exact
    // already-loaded Shift instance ShiftService#getById returned.
    @Query("SELECT cs FROM CashSession cs JOIN FETCH cs.shift WHERE cs.id = :id")
    Optional<CashSession> findWithShiftById(@Param("id") UUID id);

    @Query("SELECT cs FROM CashSession cs JOIN FETCH cs.shift ORDER BY cs.openedAt DESC")
    List<CashSession> findAllWithShiftByOrderByOpenedAtDesc();

    @Query("SELECT cs FROM CashSession cs JOIN FETCH cs.shift "
            + "WHERE cs.openedByUserId = :userId ORDER BY cs.openedAt DESC")
    List<CashSession> findAllWithShiftByOpenedByUserIdOrderByOpenedAtDesc(@Param("userId") UUID userId);

}
