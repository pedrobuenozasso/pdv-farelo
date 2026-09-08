package com.farelo.api.cashier;

import com.farelo.api.security.User;
import com.farelo.api.security.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * FARELO-307 gave this class its first two methods, {@link #open(UUID,
 * UUID)} (backing {@code POST /api/v1/cash-sessions}) and {@link
 * #getById(UUID)}/{@link #listAll()} (backing {@code GET
 * /api/v1/cash-sessions}[/{id}]). FARELO-308 adds {@link #close(UUID,
 * java.math.BigDecimal, UUID)} (backing {@code POST
 * /api/v1/cash-sessions/{id}/close}) and the {@link #findOpen(UUID)}
 * helper it shares with {@code CashMovementService} (FARELO-310/311).
 * Depends on {@link ShiftService} to
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

    public CashSessionService(
            CashSessionRepository cashSessionRepository, ShiftService shiftService, UserService userService) {
        this.cashSessionRepository = cashSessionRepository;
        this.shiftService = shiftService;
        this.userService = userService;
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
    public CashSession open(UUID shiftId, UUID actorId) {
        cashSessionRepository.findByOpenedByUserIdAndStatus(actorId, CashSessionStatus.OPEN)
                .ifPresent(existing -> {
                    throw new CashSessionAlreadyOpenException(actorId, existing.getId());
                });

        Shift shift = shiftService.getById(shiftId);
        User actor = userService.getById(actorId);

        CashSession session = new CashSession(shift, actor.getId(), actor.getName());
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
    public CashSession close(UUID id, BigDecimal closingAmount, UUID actorId) {
        CashSession session = findOpen(id);
        User actor = userService.getById(actorId);

        session.setStatus(CashSessionStatus.CLOSED);
        session.setClosingAmount(closingAmount);
        session.setClosedAt(OffsetDateTime.now(ZoneOffset.UTC));
        session.setClosedByUserId(actor.getId());
        session.setClosedByUserName(actor.getName());

        return cashSessionRepository.save(session);
    }

}
