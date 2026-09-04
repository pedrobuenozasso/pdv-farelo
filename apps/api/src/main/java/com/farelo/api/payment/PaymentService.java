package com.farelo.api.payment;

import com.farelo.api.command.Command;
import com.farelo.api.command.CommandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * FARELO-140 gave this class its first method, {@link #listByCommand(int)} —
 * read-only, backing {@code GET /api/v1/commands/{number}/payments}.
 * FARELO-141 adds this class's first producer, {@link #record(int,
 * BigDecimal, PaymentMethod)}, backing {@code POST
 * /api/v1/commands/{number}/payments} — the "Registrar pagamento manual"
 * ticket named in the prompt mestre (seção 47). FARELO-142 ("Permitir
 * múltiplos pagamentos por comanda") adds {@link #getTotalPaid(int)},
 * backing {@code GET /api/v1/commands/{number}/payments/total} — see that
 * method's javadoc for the full reasoning. Depends on {@link
 * CommandService} to resolve a business-facing comanda {@code number} into
 * a {@link Command} (404 {@code COMMAND_NOT_FOUND} via the existing {@code
 * CommandNotFoundException} when it doesn't exist) — the exact same
 * dependency direction {@code OrderService} already has on {@code
 * CommandService} for {@link com.farelo.api.ordering.OrderService#listByCommand(int)}.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final CommandService commandService;

    public PaymentService(PaymentRepository paymentRepository, CommandService commandService) {
        this.paymentRepository = paymentRepository;
        this.commandService = commandService;
    }

    /**
     * Lists every payment recorded against a comanda, oldest first — same
     * ordering convention as {@code OrderService#listByCommand}. No
     * pagination: same YAGNI reasoning already applied throughout this
     * codebase ({@code GET /api/v1/commands/{number}/orders}, {@code GET
     * /api/v1/ingredients/{ingredientId}/movements}) — the number of
     * payments per comanda is naturally small (FARELO-142 allows multiple,
     * but not an unbounded stream of them).
     */
    @Transactional(readOnly = true)
    public List<Payment> listByCommand(int commandNumber) {
        Command command = commandService.findByNumber(commandNumber);
        return paymentRepository.findByCommandOrderByCreatedAtAsc(command);
    }

    /**
     * Records a single manual payment against a comanda (FARELO-141). A pure
     * {@code INSERT} — see {@link Payment}'s javadoc for why this domain is
     * append-only (no {@code status}, no update path). This method does
     * <b>not</b> sum payments already recorded against the comanda
     * (FARELO-142) and does <b>not</b> validate a total-paid amount before
     * any future close (FARELO-143) — both explicitly out of scope for this
     * ticket; it records exactly the one payment it's given, nothing more.
     *
     * <p>{@code amount}'s "strictly positive" rule is enforced one layer up,
     * at the request DTO ({@code PaymentRequest}, {@code @Positive}) — same
     * validation-layer convention {@code InventoryMovementRequest}/{@code
     * InventoryLossRequest} already established for their own {@code
     * quantity} fields, so a caller gets a {@code 400 VALIDATION_ERROR}
     * before this method (or any transaction) ever runs, rather than this
     * method re-checking a business rule the boundary already guarantees.
     *
     * @throws com.farelo.api.command.CommandNotFoundException {@code
     *     commandNumber} doesn't exist.
     * @throws com.farelo.api.command.CommandCannotAcceptPaymentsException the
     *     comanda exists but isn't in a status that can accept a payment
     *     (see {@link CommandService#findForPayment(int)}'s javadoc for the
     *     full status-precondition reasoning).
     */
    @Transactional
    public Payment record(int commandNumber, BigDecimal amount, PaymentMethod method) {
        Command command = commandService.findForPayment(commandNumber);
        Payment payment = new Payment(command, amount, method);
        return paymentRepository.save(payment);
    }

    /**
     * FARELO-142 ("Permitir múltiplos pagamentos por comanda"): the total
     * amount paid so far against a comanda — the sum of every {@link
     * Payment} recorded for it, via {@link
     * PaymentRepository#sumAmountByCommand}. {@code Payment}/FARELO-140 and
     * {@code record}/FARELO-141 already let a comanda accumulate more than
     * one payment; what was actually missing was a real, callable
     * computation of "how much has been paid so far" — this method is that
     * computation, the exact building block FARELO-143 ("Validar total
     * pago antes de fechar") will need next.
     *
     * <p><b>Deliberately does not compute "total owed" or "is fully
     * paid"</b>: this ticket is literally "permitir múltiplos pagamentos",
     * not "calcular total devido". Checked before committing to this
     * scope: neither {@code Order} nor {@code OrderItem} expose any
     * existing notion of a comanda's total bill (no sum-of-order-item-
     * prices query anywhere in {@code ordering}), so that computation
     * doesn't exist yet anywhere in this codebase — building it is not
     * this ticket's job. Comparing "paid" against "owed" to decide whether
     * a comanda is fully settled is exactly what FARELO-143 needs to do
     * when it wires this total into {@code CommandService#close}'s
     * validation, so that comparison (and whatever "total owed" query it
     * requires) belongs there, not here.
     *
     * <p><b>Not status-gated, unlike {@link #record}</b>: resolves the
     * comanda via {@link CommandService#findByNumber(int)} — same method
     * {@link #listByCommand(int)} already uses — not {@code
     * findForPayment}. This is a pure read of an already-recorded ledger,
     * not an action that requires the comanda to currently be payable
     * (e.g. a caller should still be able to ask "how much was paid" for a
     * comanda that's already {@code CLOSED}).
     *
     * @return the sum of every payment recorded for {@code commandNumber},
     *     never {@code null} — {@code 0} (not {@code null}) when the
     *     comanda has no payments yet, per {@code
     *     sumAmountByCommand}'s {@code COALESCE(SUM(...), 0)}.
     * @throws com.farelo.api.command.CommandNotFoundException {@code
     *     commandNumber} doesn't exist.
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalPaid(int commandNumber) {
        Command command = commandService.findByNumber(commandNumber);
        return paymentRepository.sumAmountByCommand(command);
    }

}
