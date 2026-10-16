package com.farelo.api.cashier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CashMovementRepository extends JpaRepository<CashMovement, UUID> {

    @Query("SELECT COALESCE(SUM(m.amount), 0) FROM CashMovement m "
            + "WHERE m.cashSession = :session AND m.type = :type")
    BigDecimal sumAmountByCashSessionAndType(
            @Param("session") CashSession session, @Param("type") CashMovementType type);

    // Backs GET /api/v1/cash-sessions/{id}/movements
    // (CashMovementService#listByCashSession). No JOIN FETCH needed here —
    // unlike CashSessionRepository#findWithShiftById, CashMovementResponse
    // only reads cashSession.getId() (see that record's javadoc), and a
    // JPA @ManyToOne proxy's id is always available without triggering
    // lazy initialization (Hibernate populates it from the FK column
    // itself, no extra query).
    List<CashMovement> findByCashSessionOrderByCreatedAtAsc(CashSession cashSession);

}
