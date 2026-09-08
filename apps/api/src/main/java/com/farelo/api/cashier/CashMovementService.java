package com.farelo.api.cashier;

import com.farelo.api.audit.AuditLogService;
import com.farelo.api.security.User;
import com.farelo.api.security.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * FARELO-310 ("Sangria e Suprimento") gave this class its first two
 * producers, {@link #recordWithdrawal} and {@link #recordSupply}, plus
 * {@link #listByCashSession} (backing {@code GET
 * /api/v1/cash-sessions/{id}/movements}). Both producers delegate to the
 * private {@link #record} helper, which {@link #recordExpense}/{@link
 * #recordIncome} (FARELO-311) reuse unchanged — see {@link CashMovement}'s
 * javadoc for why all four {@link CashMovementType} values already exist
 * on the entity/table as of FARELO-310.
 *
 * <p>Depends on {@link CashSessionService#findOpen(UUID)} to resolve and
 * validate the target session (409 {@link CashSessionNotOpenException} if
 * it isn't {@code OPEN} — sangria/suprimento/despesa/receita all require an
 * open drawer, same precondition {@code CashSessionService#close} already
 * uses), on {@link UserService} to resolve the acting user (same {@code
 * UUID actorId} pattern {@code PaymentService#record}/{@code
 * DiscountService} already use), and on {@link AuditLogService} — FARELO-310's
 * own text explicitly asks for this ("auditável, ver AuditLogService, mesmo
 * seam já usado por Payment/Order-cancel").
 */
@Service
public class CashMovementService {

    private static final String AUDIT_ENTITY_TYPE = "CashMovement";

    private final CashMovementRepository cashMovementRepository;
    private final CashSessionService cashSessionService;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public CashMovementService(
            CashMovementRepository cashMovementRepository,
            CashSessionService cashSessionService,
            UserService userService,
            AuditLogService auditLogService,
            ObjectMapper objectMapper) {
        this.cashMovementRepository = cashMovementRepository;
        this.cashSessionService = cashSessionService;
        this.userService = userService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    /**
     * Sangria — cash removed from the drawer (e.g. a security deposit at
     * the bank), recorded against an open {@link CashSession}.
     *
     * @throws CashSessionNotFoundException {@code cashSessionId} doesn't
     *     exist.
     * @throws CashSessionNotOpenException the session isn't {@code OPEN}.
     */
    @Transactional
    public CashMovement recordWithdrawal(UUID cashSessionId, BigDecimal amount, String description, UUID actorId) {
        return record(cashSessionId, CashMovementType.WITHDRAWAL, amount, description, actorId);
    }

    /**
     * Suprimento — cash added to reinforce the drawer's float, recorded
     * against an open {@link CashSession}.
     *
     * @throws CashSessionNotFoundException {@code cashSessionId} doesn't
     *     exist.
     * @throws CashSessionNotOpenException the session isn't {@code OPEN}.
     */
    @Transactional
    public CashMovement recordSupply(UUID cashSessionId, BigDecimal amount, String description, UUID actorId) {
        return record(cashSessionId, CashMovementType.SUPPLY, amount, description, actorId);
    }

    private CashMovement record(
            UUID cashSessionId, CashMovementType type, BigDecimal amount, String description, UUID actorId) {
        CashSession session = cashSessionService.findOpen(cashSessionId);
        User actor = userService.getById(actorId);

        CashMovement movement = new CashMovement(session, type, amount, description, actor.getId(), actor.getName());
        CashMovement saved = cashMovementRepository.save(movement);

        auditLogService.record(
                actor, auditAction(type), AUDIT_ENTITY_TYPE, saved.getId(), null, serialize(saved));

        return saved;
    }

    // FARELO-310/311: one AUDIT_ACTION per CashMovementType, same
    // per-producer naming convention PaymentService (PAYMENT_RECORDED)
    // already established.
    private static String auditAction(CashMovementType type) {
        return switch (type) {
            case WITHDRAWAL -> "CASH_WITHDRAWAL_RECORDED";
            case SUPPLY -> "CASH_SUPPLY_RECORDED";
            case EXPENSE -> "CASH_EXPENSE_RECORDED";
            case INCOME -> "CASH_INCOME_RECORDED";
        };
    }

    private String serialize(CashMovement movement) {
        try {
            return objectMapper.writeValueAsString(
                    new CashMovementSnapshot(movement.getType(), movement.getAmount(), movement.getDescription()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize cash movement snapshot", e);
        }
    }

    /**
     * Lists every movement recorded against a cash session, oldest first —
     * same ordering convention as {@code PaymentService#listByCommand}.
     * Backs {@code GET /api/v1/cash-sessions/{id}/movements}.
     *
     * <p>Uses {@link CashSessionService#getById(UUID)} (not {@code
     * findOpen}) to resolve the session — a pure read over an already-
     * recorded ledger, callable regardless of the session's current status
     * (e.g. still useful to inspect after the session is {@code CLOSED}),
     * same "not status-gated" reasoning {@code PaymentService#getTotalPaid}
     * already established for its own read.
     *
     * @throws CashSessionNotFoundException {@code cashSessionId} doesn't
     *     exist.
     */
    @Transactional(readOnly = true)
    public List<CashMovement> listByCashSession(UUID cashSessionId) {
        CashSession session = cashSessionService.getById(cashSessionId);
        return cashMovementRepository.findByCashSessionOrderByCreatedAtAsc(session);
    }

}
