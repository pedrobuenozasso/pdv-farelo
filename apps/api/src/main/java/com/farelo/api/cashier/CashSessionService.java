package com.farelo.api.cashier;

import com.farelo.api.payment.PaymentMethod;
import com.farelo.api.payment.PaymentRepository;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.UserService;
import com.farelo.api.security.rbac.InsufficientRoleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * FARELO-307 gave this class its first two methods, {@link #open(UUID,
 * BigDecimal, UUID)} (backing {@code POST /api/v1/cash-sessions}) and {@link
 * #getById(UUID)}/{@link #listAll()} (backing {@code GET
 * /api/v1/cash-sessions}[/{id}]). FARELO-308 adds {@link #close(UUID,
 * java.math.BigDecimal, UUID, UserRole)} (backing {@code POST
 * /api/v1/cash-sessions/{id}/close}) and the {@link #findOpen(UUID)}
 * helper it shares with {@code CashMovementService} (FARELO-310/311).
 * FARELO-309 adds {@link #reconcile(UUID, UUID)} (backing {@code POST
 * /api/v1/cash-sessions/{id}/reconcile}) and its {@link #findClosed(UUID)}
 * helper — see {@link #reconcile(UUID, UUID)}'s javadoc for the full
 * comparison/scope reasoning, including the one new dependency this adds,
 * on {@link PaymentRepository} (read-only). Depends on {@link ShiftService} to
 * resolve the {@code shiftId} given at open time (404 {@link
 * ShiftNotFoundException} when it doesn't exist) and on {@link UserService}
 * to resolve the acting operator's current name (same {@code UUID actorId}
 * → {@code userService.getById(actorId)} pattern {@code
 * DiscountService#applyFixedAmount}/{@code #applyPercentage} already use).
 *
 * <h2>"Um caixa aberto por vez por operador" (FARELO-307)</h2>
 *
 * The ticket text explicitly asks for this rule to be decided, not copied
 * 1:1 from Sischef. Decision: scoped to the <em>operator</em>, not the
 * {@link Shift} — {@link CashSessionRepository#findByOpenedByUserIdAndStatus}
 * checks whether the acting user already has an {@link
 * CashSessionStatus#OPEN} session before allowing a new one; if so, 409
 * {@link CashSessionAlreadyOpenException}. Deliberately <b>not</b>
 * restricted per {@link Shift}: the same named template (e.g. "TURNO 01")
 * can legitimately be reused by several different operators opening
 * concurrent, independent cash drawers — the rule that makes operational
 * sense is "one drawer per person", not "one drawer per template", which
 * matches {@code docs/gaps-sischef-pdv.md}'s own description of a Turno as
 * something "reaproveitado a cada abertura" (implying concurrent reuse is
 * expected, not prevented).
 *
 * <p><b>Note: read-check-write without a locking strategy</b>, same
 * accepted non-atomicity already noted on {@code CommandService#open} —
 * two near-simultaneous open requests from the same operator could both
 * observe no existing open session and both succeed. Out of scope for this
 * ticket; revisit with optimistic locking if it becomes a real concern.
 */
@Service
public class CashSessionService {

    private final CashSessionRepository cashSessionRepository;
    private final ShiftService shiftService;
    private final UserService userService;
    private final PaymentRepository paymentRepository;
    private final CashMovementRepository cashMovementRepository;

    public CashSessionService(
            CashSessionRepository cashSessionRepository,
            ShiftService shiftService,
            UserService userService,
            PaymentRepository paymentRepository,
            CashMovementRepository cashMovementRepository) {
        this.cashSessionRepository = cashSessionRepository;
        this.shiftService = shiftService;
        this.userService = userService;
        this.paymentRepository = paymentRepository;
        this.cashMovementRepository = cashMovementRepository;
    }

    /**
     * Opens a new {@link CashSession} for the acting operator against
     * {@code shiftId}.
     *
     * @throws ShiftNotFoundException {@code shiftId} doesn't exist.
     * @throws CashSessionAlreadyOpenException the operator already has an
     *     {@code OPEN} session — see this class's javadoc for the full
     *     "one per operator, not per shift" reasoning.
     */
    @Transactional
    public CashSession open(UUID shiftId, BigDecimal openingAmount, UUID actorId) {
        cashSessionRepository.findByOpenedByUserIdAndStatus(actorId, CashSessionStatus.OPEN)
                .ifPresent(existing -> {
                    throw new CashSessionAlreadyOpenException(actorId, existing.getId());
                });

        Shift shift = shiftService.getById(shiftId);
        User actor = userService.getById(actorId);

        CashSession session = new CashSession(shift, actor.getId(), actor.getName(), openingAmount);
        return cashSessionRepository.save(session);
    }

    /**
     * Backs {@code GET /api/v1/cash-sessions/{id}}. Uses {@link
     * CashSessionRepository#findWithShiftById} (not the inherited plain
     * {@code findById}) so {@code CashSessionResponse#from} can read
     * {@code session.getShift().getName()} after this (short) read-only
     * transaction has already closed — see that repository method's
     * javadoc for the full {@code open-in-view=false} reasoning.
     */
    @Transactional(readOnly = true)
    public CashSession getById(UUID id) {
        return cashSessionRepository.findWithShiftById(id)
                .orElseThrow(() -> new CashSessionNotFoundException(id));
    }

    /**
     * Backs {@code GET /api/v1/cash-sessions}. Newest first ({@code
     * openedAt DESC}) — same "what just happened" reasoning {@code
     * AuditLogService#list}'s javadoc already gives for its own
     * newest-first ordering, and unlike {@code
     * OrderService#listByCommand}'s oldest-first FIFO (a queue meant to be
     * drained in order) — a caixa dashboard almost always wants the most
     * recently opened session first.
     */
    @Transactional(readOnly = true)
    public List<CashSession> listAll() {
        return cashSessionRepository.findAllWithShiftByOrderByOpenedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<CashSession> listVisibleTo(UUID actorId, UserRole role) {
        if (role == UserRole.ADMIN || role == UserRole.MANAGER) {
            return listAll();
        }
        if (role == UserRole.CASHIER) {
            return cashSessionRepository.findAllWithShiftByOpenedByUserIdOrderByOpenedAtDesc(actorId);
        }
        throw new InsufficientRoleException(role);
    }

    @Transactional(readOnly = true)
    public CashSession getAccessibleById(UUID id, UUID actorId, UserRole role) {
        CashSession session = getById(id);
        requireAccess(session, actorId, role);
        return session;
    }

    public CashSession findOpenAccessible(UUID id, UUID actorId, UserRole role) {
        CashSession session = getAccessibleById(id, actorId, role);
        if (session.getStatus() != CashSessionStatus.OPEN) {
            throw new CashSessionNotOpenException(id, session.getStatus());
        }
        return session;
    }

    /** Serializes payment, movement and close writes on the same drawer row. */
    @Transactional
    public CashSession findOpenAccessibleForUpdate(UUID id, UUID actorId, UserRole role) {
        CashSession session = cashSessionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CashSessionNotFoundException(id));
        session.getShift().getName(); // initialize for CashSessionResponse after the transaction
        requireAccess(session, actorId, role);
        if (session.getStatus() != CashSessionStatus.OPEN) {
            throw new CashSessionNotOpenException(id, session.getStatus());
        }
        return session;
    }

    private void requireAccess(CashSession session, UUID actorId, UserRole role) {
        if (role == UserRole.ADMIN || role == UserRole.MANAGER
                || (role == UserRole.CASHIER && actorId.equals(session.getOpenedByUserId()))) {
            return;
        }
        throw new InsufficientRoleException(role);
    }

    /**
     * Resolves a {@link CashSession} that's ready to be closed (FARELO-308)
     * or to have a {@code CashMovement} recorded against it (FARELO-310/
     * 311: sangria/suprimento/despesa/receita all require an open session)
     * — same "resolve + validate, no save" shape as {@code
     * CommandService#findForPayment}/{@code #findClosable}. Uses {@link
     * CashSessionRepository#findWithShiftById} (not plain {@code findById})
     * so the returned session's {@code shift} is safe to read after this
     * method's transaction (or its caller's) has closed.
     *
     * @throws CashSessionNotFoundException {@code id} doesn't exist.
     * @throws CashSessionNotOpenException the session exists but isn't
     *     {@code OPEN} (already {@code CLOSED}).
     */
    public CashSession findOpen(UUID id) {
        CashSession session = findWithShift(id);
        if (session.getStatus() != CashSessionStatus.OPEN) {
            throw new CashSessionNotOpenException(id, session.getStatus());
        }
        return session;
    }

    private CashSession findWithShift(UUID id) {
        return cashSessionRepository.findWithShiftById(id)
                .orElseThrow(() -> new CashSessionNotFoundException(id));
    }

    /**
     * Closes a {@link CashSession} (FARELO-308) — records the physical cash
     * amount counted ({@code closingAmount}), {@code closedAt}, and who
     * closed it. Does <b>not</b> touch {@code reconciliationStatus} — it
     * stays {@code PENDING} until a separate reconciliation call
     * (FARELO-309, a future ticket as of this one), matching {@code
     * docs/gaps-sischef-pdv.md}'s "fica com status Não conferido até
     * alguém rodar a conferência".
     *
     * @throws CashSessionNotFoundException {@code id} doesn't exist.
     * @throws CashSessionNotOpenException the session isn't {@code OPEN}
     *     (already closed).
     */
    @Transactional
    public CashSession close(UUID id, BigDecimal closingAmount, UUID actorId, UserRole role) {
        CashSession session = findOpenAccessibleForUpdate(id, actorId, role);
        User actor = userService.getById(actorId);

        session.setStatus(CashSessionStatus.CLOSED);
        session.setClosingAmount(closingAmount);
        session.setClosedAt(OffsetDateTime.now(ZoneOffset.UTC));
        session.setClosedByUserId(actor.getId());
        session.setClosedByUserName(actor.getName());

        return cashSessionRepository.save(session);
    }

    /**
     * Reconciles a closed {@link CashSession} (FARELO-309, "Conferência de
     * caixa") — a step deliberately separate from {@link #close}, matching
     * {@code docs/gaps-sischef-pdv.md}'s "passo separado do fechamento".
     * Compares {@code closingAmount} (the physical cash counted, informed
     * at close time) against {@code expectedAmount} (the system-calculated
     * figure computed here): opening float plus cash payments linked to
     * this session, supplies and income, minus withdrawals and expenses.
     * Historical sessions without a recorded opening float cannot be
     * reconciled automatically.
     *
     * <p><b>No tolerance</b>: {@code MATCHED} requires the difference to be
     * exactly zero ({@link BigDecimal#compareTo}); any other value,
     * positive or negative, is {@code MISMATCHED}. Runs exactly once — see
     * {@link CashSessionAlreadyReconciledException}'s javadoc.
     *
     * @throws CashSessionNotFoundException {@code id} doesn't exist.
     * @throws CashSessionNotClosedException the session isn't {@code
     *     CLOSED} yet.
     * @throws CashSessionAlreadyReconciledException the session was already
     *     reconciled.
     */
    @Transactional
    public CashSession reconcile(UUID id, UUID actorId) {
        CashSession session = findClosed(id);
        if (session.getReconciliationStatus() != CashSessionReconciliationStatus.PENDING) {
            throw new CashSessionAlreadyReconciledException(id);
        }

        User actor = userService.getById(actorId);

        if (session.getOpeningAmount() == null) {
            throw new CashSessionOpeningAmountUnknownException(id);
        }

        BigDecimal expectedAmount = session.getOpeningAmount()
                .add(paymentRepository.sumAmountByCashSessionAndMethod(session, PaymentMethod.CASH))
                .add(cashMovementRepository.sumAmountByCashSessionAndType(session, CashMovementType.SUPPLY))
                .add(cashMovementRepository.sumAmountByCashSessionAndType(session, CashMovementType.INCOME))
                .subtract(cashMovementRepository.sumAmountByCashSessionAndType(session, CashMovementType.WITHDRAWAL))
                .subtract(cashMovementRepository.sumAmountByCashSessionAndType(session, CashMovementType.EXPENSE));
        BigDecimal difference = session.getClosingAmount().subtract(expectedAmount);
        CashSessionReconciliationStatus result = difference.compareTo(BigDecimal.ZERO) == 0
                ? CashSessionReconciliationStatus.MATCHED
                : CashSessionReconciliationStatus.MISMATCHED;

        session.setExpectedAmount(expectedAmount);
        session.setReconciliationDifference(difference);
        session.setReconciliationStatus(result);
        session.setReconciledAt(OffsetDateTime.now(ZoneOffset.UTC));
        session.setReconciledByUserId(actor.getId());
        session.setReconciledByUserName(actor.getName());

        return cashSessionRepository.save(session);
    }

    /**
     * Resolves a {@link CashSession} ready to be reconciled — the status
     * half of {@link #reconcile}'s precondition, same "resolve + validate,
     * no save" shape as {@link #findOpen(UUID)}.
     *
     * @throws CashSessionNotFoundException {@code id} doesn't exist.
     * @throws CashSessionNotClosedException the session isn't {@code
     *     CLOSED} yet.
     */
    public CashSession findClosed(UUID id) {
        CashSession session = findWithShift(id);
        if (session.getStatus() != CashSessionStatus.CLOSED) {
            throw new CashSessionNotClosedException(id, session.getStatus());
        }
        return session;
    }

}
