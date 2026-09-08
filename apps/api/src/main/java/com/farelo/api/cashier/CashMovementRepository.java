package com.farelo.api.cashier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CashMovementRepository extends JpaRepository<CashMovement, UUID> {

    // Backs GET /api/v1/cash-sessions/{id}/movements
    // (CashMovementService#listByCashSession). No JOIN FETCH needed here —
    // unlike CashSessionRepository#findWithShiftById, CashMovementResponse
    // only reads cashSession.getId() (see that record's javadoc), and a
    // JPA @ManyToOne proxy's id is always available without triggering
    // lazy initialization (Hibernate populates it from the FK column
    // itself, no extra query).
    List<CashMovement> findByCashSessionOrderByCreatedAtAsc(CashSession cashSession);

}
