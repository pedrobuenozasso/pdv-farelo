package com.farelo.api.payment;

import com.farelo.api.command.Command;
import com.farelo.api.command.CommandNotFullyPaidException;
import com.farelo.api.command.CommandService;
import com.farelo.api.ordering.OrderService;
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
 *
 * <p><b>FARELO-143 ("Validar total pago antes de fechar") adds a second
 * cross-domain dependency, on {@link OrderService}</b> — see {@link
 * #closeCommand(int)}'s javadoc for the full reasoning on why that method
 * (not {@code CommandService#close}) is where the payment-sufficiency check
 * lives, and why {@code payment} depending on {@code ordering} is safe (no
 * cycle) even though {@code payment} already depends on {@code command} and
 * {@code ordering} already depends on {@code command} too.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final CommandService commandService;
    private final OrderService orderService;

    public PaymentService(PaymentRepository paymentRepository, CommandService commandService, OrderService orderService) {
        this.paymentRepository = paymentRepository;
        this.commandService = commandService;
        this.orderService = orderService;
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

    /**
     * FARELO-223 ("Calcular saldo restante"): a comanda's {@link
     * PaymentBalance} — {@code totalOwed}/{@code totalPaid}/{@code
     * remaining}, all computed here rather than by a caller. See {@link
     * PaymentBalance}'s javadoc for why this method exists at all: before
     * it, nothing in this codebase computed {@code totalOwed} except
     * {@link #closeCommand(int)}'s internal validation, and {@code
     * remaining} wasn't computed anywhere server-side — the PDV frontend
     * derived both itself from raw order/payment data. Backs {@code GET
     * /api/v1/commands/{number}/payments/balance}.
     *
     * <p>Same "not status-gated" reasoning as {@link #getTotalPaid(int)}/
     * {@link OrderService#getTotalOwed(int)} — a pure read, callable
     * regardless of the comanda's current status (e.g. still useful to
     * inspect after a comanda is already {@code CLOSED}).
     *
     * @throws com.farelo.api.command.CommandNotFoundException {@code
     *     commandNumber} doesn't exist.
     */
    @Transactional(readOnly = true)
    public PaymentBalance getBalance(int commandNumber) {
        BigDecimal totalOwed = orderService.getTotalOwed(commandNumber);
        BigDecimal totalPaid = getTotalPaid(commandNumber);
        return PaymentBalance.of(totalOwed, totalPaid);
    }

    /**
     * FARELO-143 ("Validar total pago antes de fechar"): closes a comanda,
     * but only after validating its total paid ({@link #getTotalPaid(int)},
     * FARELO-142) covers its total owed ({@link
     * OrderService#getTotalOwed(int)}, new in this ticket). This is the real
     * production entry point behind {@code POST
     * /api/v1/commands/{number}/close} as of this ticket — see {@code
     * com.farelo.api.payment.web.PaymentController#close}, which now owns
     * that route (moved out of {@code
     * com.farelo.api.command.web.CommandController}; see that controller's
     * updated javadoc and this method's "why here, not CommandService"
     * section below).
     *
     * <p><b>Order of checks</b>: status first, then payment — mirrors the
     * pre-FARELO-143 behavior for the status half exactly. {@link
     * CommandService#findClosable(int)} resolves the comanda (404 {@code
     * COMMAND_NOT_FOUND}) and validates it's in a closable status (409
     * {@code COMMAND_CANNOT_BE_CLOSED}) <em>before</em> this method computes
     * either total — so a comanda that's e.g. still {@code AVAILABLE} still
     * fails with the same status error it always did, never a confusing
     * payment error about a comanda nothing has been ordered or paid for
     * yet. Only once the comanda is confirmed closable does this method
     * compare {@code totalPaid >= totalOwed}; {@link
     * CommandService#close(int)} performs the actual transition+save at the
     * end, re-validating the same status precondition (cheap, in-memory,
     * same accepted non-atomicity as every other read-check-write method in
     * this codebase — see {@code CommandService#open}'s comment).
     *
     * <p><b>Comparison semantics: {@code totalPaid >= totalOwed}, not exact
     * equality.</b> Prompt mestre seção 30 (Transações) doesn't specify this
     * either way — it's entirely about transaction boundaries, not payment
     * amounts. {@code >=} is the more permissive, operationally realistic
     * choice for a coffee shop: a customer pays R$2 more than the bill and
     * declines change (a de facto tip), a card terminal only accepts whole
     * amounts and someone rounds up, or a cashier deliberately over-collects
     * to cover a rounding difference — none of these should block closing
     * the tab. Requiring exact equality would make an overpaid-by-a-few-
     * cents comanda permanently unclosable without a compensating negative
     * entry, and this ledger is append-only (see {@link Payment}'s
     * javadoc) — there is no way to "correct" an overpayment down to the
     * exact owed amount even if that were desired. Under-payment by any
     * amount, however strict {@code >=} still is, correctly blocks closing.
     *
     * <p><b>Zero orders still closes fine.</b> An {@code AVAILABLE}/{@code
     * OPEN} comanda nothing has ever been ordered against has {@code
     * totalOwed = 0} ({@code OrderService#getTotalOwed}'s {@code
     * COALESCE(SUM(...), 0)}) and, almost always, {@code totalPaid = 0} too
     * — {@code 0 >= 0} holds trivially, so this check never blocks closing a
     * comanda that was opened but never ordered against, preserving
     * FARELO-034's original behavior for that case exactly.
     *
     * <p><b>Why the orchestration lives here, in {@code PaymentService}, not
     * in {@code CommandService#close} itself</b> — the central architectural
     * decision of this ticket, checked against the actual dependency graph
     * rather than assumed: before this ticket, {@code command} depended on
     * nothing cross-domain, while both {@code ordering} ({@code
     * OrderService} → {@code CommandService}) and {@code payment} ({@code
     * PaymentService} → {@code CommandService}) already depended on {@code
     * command}. Making {@code CommandService} itself call into {@code
     * PaymentService} (as the ticket's title might suggest literally) would
     * require {@code CommandService}'s constructor to accept a {@code
     * PaymentService} bean — but {@code PaymentService}'s constructor
     * already accepts a {@code CommandService} bean, so that's a genuine
     * Spring circular bean dependency ({@code CommandService} →
     * {@code PaymentService} → {@code CommandService}), not just a style
     * concern: with constructor injection and no {@code
     * spring.main.allow-circular-references} override (confirmed absent
     * from {@code application.yml}), the application context would flat-out
     * fail to start. The same problem would exist for {@code CommandService}
     * depending on {@code OrderService} for "total owed" (({@code
     * OrderService} → {@code CommandService} already exists too).
     *
     * <p>The one direction that stays acyclic is {@code payment} depending
     * on {@code ordering} in addition to {@code command}: {@code
     * OrderService} has no dependency on {@code PaymentService}, so {@code
     * PaymentService} → {@code OrderService} → {@code CommandService} is a
     * straight line, not a cycle. This mirrors — at the service-layer
     * instead of controller-layer — the exact reasoning {@code
     * CommandOrdersController} and {@code PaymentController} (FARELO-055/
     * FARELO-140) already used to justify keeping command-scoped read
     * endpoints inside the domain that depends on {@code command}, rather
     * than adding a dependency in the opposite direction into {@code
     * command.web}. Applying that same philosophy consistently here is also
     * why the {@code POST .../close} route itself moved into {@code
     * payment.web.PaymentController}: letting {@code
     * command.web.CommandController} depend on {@code PaymentService}
     * instead would have avoided the Spring-level cycle (nothing depends on
     * a controller bean) but reintroduced exactly the domain-direction
     * inconsistency ({@code command} depending on a domain that depends on
     * it) this codebase has consistently avoided everywhere else. {@code
     * CommandService#close(int)} remains the low-level, payment-agnostic
     * state-transition primitive it always was — unchanged behavior, still
     * usable on its own if a future caller ever needs a close without a
     * payment check (none does today).
     *
     * @throws com.farelo.api.command.CommandNotFoundException {@code
     *     commandNumber} doesn't exist.
     * @throws com.farelo.api.command.CommandCannotBeClosedException the
     *     comanda exists but isn't in a closable status (see {@link
     *     CommandService#findClosable(int)}).
     * @throws CommandNotFullyPaidException the comanda is closable but its
     *     total paid is less than its total owed.
     */
    @Transactional
    public Command closeCommand(int commandNumber) {
        // Status precondition first — see this method's javadoc for why.
        // Return value discarded: only the validation side effect (404/409)
        // matters here; close() below re-resolves the Command by number.
        commandService.findClosable(commandNumber);

        BigDecimal totalOwed = orderService.getTotalOwed(commandNumber);
        BigDecimal totalPaid = getTotalPaid(commandNumber);

        if (totalPaid.compareTo(totalOwed) < 0) {
            throw new CommandNotFullyPaidException(commandNumber, totalOwed, totalPaid);
        }

        return commandService.close(commandNumber);
    }

}
