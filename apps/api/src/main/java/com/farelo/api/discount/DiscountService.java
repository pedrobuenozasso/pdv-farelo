package com.farelo.api.discount;

import com.farelo.api.command.Command;
import com.farelo.api.command.CommandService;
import com.farelo.api.ordering.OrderService;
import com.farelo.api.security.User;
import com.farelo.api.security.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Applies and lists {@link Discount}s against a comanda (FARELO-230/231/232).
 * Backs {@code POST}/{@code GET /api/v1/commands/{number}/discounts}.
 *
 * <p>Depends on {@link CommandService} (resolve/validate the comanda, same
 * {@link CommandService#findForPayment(int)} precondition {@code
 * PaymentService#record} already uses — applying a discount is the same
 * class of financial adjustment as recording a payment, so it reuses that
 * existing status check rather than a new, parallel {@code
 * CommandCannotAcceptDiscountsException} for the identical rule) and on
 * {@link OrderService#getTotalOwed(int)} (what a percentage discount's
 * rate is computed against). {@code payment} depends on this class in turn
 * ({@code PaymentService#getBalance}/{@code #closeCommand}, FARELO-223/224
 * extended to subtract the total discount) — {@code payment → discount →
 * {command, ordering}} stays a straight line, no Spring circular-bean
 * dependency (same reasoning already worked through in {@code
 * PaymentService#closeCommand}'s javadoc for its own {@code ordering}
 * dependency).
 *
 * <h2>Percentage computed against the raw {@code totalOwed}, not
 * previously-discounted total</h2>
 *
 * If a comanda already has one discount applied and a second is requested,
 * this class computes the second discount's percentage against the same
 * {@code OrderService#getTotalOwed(int)} figure as the first — not against
 * {@code totalOwed} minus the first discount. Chaining discounts
 * (percentage-of-remaining-after-previous-discount) is a real design
 * choice with no example or requirement in the ticket text to settle it
 * either way; computing against the raw total is simpler, more
 * predictable for a cashier ("10% off the R$95 bill" always means R$9,50,
 * regardless of what else was already discounted), and avoids silently
 * compounding discounts into a much larger total reduction than either one
 * alone would suggest. Revisit if a future ticket specifies chaining
 * behavior explicitly.
 */
@Service
public class DiscountService {

    private final DiscountRepository discountRepository;
    private final CommandService commandService;
    private final OrderService orderService;
    private final UserService userService;

    public DiscountService(
            DiscountRepository discountRepository,
            CommandService commandService,
            OrderService orderService,
            UserService userService) {
        this.discountRepository = discountRepository;
        this.commandService = commandService;
        this.orderService = orderService;
        this.userService = userService;
    }

    /**
     * Applies a {@link DiscountType#FIXED_AMOUNT} discount (FARELO-230).
     * {@code amount} is recorded verbatim as {@code discountedAmount} — no
     * cap against the comanda's current total owed; the ticket doesn't ask
     * for one, and {@code PaymentBalance#remaining} already floors at zero
     * regardless (see that record's javadoc), so an over-large discount
     * simply zeroes what's owed rather than producing a negative or
     * crashing.
     *
     * @throws com.farelo.api.command.CommandNotFoundException {@code
     *     commandNumber} doesn't exist.
     * @throws com.farelo.api.command.CommandCannotAcceptPaymentsException
     *     the comanda isn't in a status that can accept a financial
     *     adjustment (see {@link CommandService#findForPayment(int)}).
     */
    @Transactional
    public Discount applyFixedAmount(int commandNumber, BigDecimal amount, String reason, UUID actorId) {
        Command command = commandService.findForPayment(commandNumber);
        BigDecimal totalOwed = orderService.getTotalOwed(commandNumber);
        User actor = userService.getById(actorId);

        Discount discount = new Discount(
                command, DiscountType.FIXED_AMOUNT, null, totalOwed, amount, reason,
                actor.getId(), actor.getName());
        return discountRepository.save(discount);
    }

    /**
     * Applies a {@link DiscountType#PERCENTAGE} discount (FARELO-231).
     * {@code discountedAmount = totalOwed * percentage / 100}, computed
     * with {@link BigDecimal} (the ticket's own requirement) and rounded
     * to 2 decimal places ({@link RoundingMode#HALF_UP}, the same rounding
     * convention money amounts use elsewhere in this codebase — e.g.
     * fiscal calculations). See class javadoc for why {@code totalOwed} is
     * always the raw, undiscounted figure.
     *
     * @throws com.farelo.api.command.CommandNotFoundException {@code
     *     commandNumber} doesn't exist.
     * @throws com.farelo.api.command.CommandCannotAcceptPaymentsException
     *     same precondition as {@link #applyFixedAmount}.
     */
    @Transactional
    public Discount applyPercentage(int commandNumber, BigDecimal percentage, String reason, UUID actorId) {
        Command command = commandService.findForPayment(commandNumber);
        BigDecimal totalOwed = orderService.getTotalOwed(commandNumber);
        BigDecimal discountedAmount = totalOwed
                .multiply(percentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        User actor = userService.getById(actorId);

        Discount discount = new Discount(
                command, DiscountType.PERCENTAGE, percentage, totalOwed, discountedAmount, reason,
                actor.getId(), actor.getName());
        return discountRepository.save(discount);
    }

    @Transactional(readOnly = true)
    public List<Discount> listByCommand(int commandNumber) {
        Command command = commandService.findByNumber(commandNumber);
        return discountRepository.findByCommandOrderByCreatedAtAsc(command);
    }

    /**
     * The total discount applied so far against a comanda — sum of every
     * {@link Discount#getDiscountedAmount()} recorded for it. Used by
     * {@code PaymentService#getBalance}/{@code #closeCommand} (FARELO-223/
     * 224) to reduce what's owed. Not status-gated, same "pure read over
     * an already-recorded ledger" reasoning as {@code
     * PaymentService#getTotalPaid}.
     *
     * @throws com.farelo.api.command.CommandNotFoundException {@code
     *     commandNumber} doesn't exist.
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalDiscount(int commandNumber) {
        Command command = commandService.findByNumber(commandNumber);
        return discountRepository.sumDiscountedAmountByCommand(command);
    }

}
