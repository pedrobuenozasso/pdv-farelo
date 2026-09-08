package com.farelo.api.cashier;

import com.farelo.api.security.User;
import com.farelo.api.security.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * FARELO-307 gave this class its first two methods, {@link #open(UUID,
 * UUID)} (backing {@code POST /api/v1/cash-sessions}) and {@link
 * #getById(UUID)}/{@link #listAll()} (backing {@code GET
 * /api/v1/cash-sessions}[/{id}]). Depends on {@link ShiftService} to
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

}
