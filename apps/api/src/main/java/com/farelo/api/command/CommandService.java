package com.farelo.api.command;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

@Service
public class CommandService {

    // Origin statuses from which a command can be closed. Both make
    // operational sense: a command can be closed straight from OPEN, or
    // after payment was requested (FARELO-034). Purely a status check —
    // this class still has no opinion on whether the comanda has been paid
    // enough; that check lives in PaymentService#closeCommand (FARELO-143),
    // which calls findClosable() below for this precondition before its own
    // payment-sufficiency check, then close() to actually transition. See
    // findClosable()'s javadoc for why this class doesn't perform that
    // payment check itself.
    private static final Set<CommandStatus> CLOSABLE_STATUSES =
            EnumSet.of(CommandStatus.OPEN, CommandStatus.PAYMENT_REQUESTED);

    // FARELO-141: statuses from which a payment can be recorded. Same two
    // values as CLOSABLE_STATUSES above, kept as its own constant rather
    // than reused directly — the two answer different domain questions
    // ("can this be closed" vs. "can this accept a payment") that only
    // happen to agree today. AVAILABLE is excluded: nothing has been
    // ordered yet, so there is nothing to pay for. CLOSED is excluded: the
    // tab is already settled — a payment "against" an already-closed
    // command reads backwards (if a payment was missed before closing,
    // that's a data-entry mistake to fix by correcting the close, not by
    // bolting a payment onto a command that's conceptually done). BLOCKED
    // is excluded for the same reason it is excluded from
    // CommandCannotAcceptOrdersException's valid set — a blocked command
    // isn't in normal operational use. That leaves exactly OPEN (the
    // common case — a payment recorded while the comanda is still being
    // used) and PAYMENT_REQUESTED (a payment recorded after staff marked
    // the comanda as awaiting payment, whenever a future ticket adds that
    // transition) — the same window in which FARELO-034 already allows
    // close(), which makes sense: paying and closing are two steps of the
    // same "settling the tab" operation, so they share the same valid
    // origin states.
    private static final Set<CommandStatus> PAYABLE_STATUSES =
            EnumSet.of(CommandStatus.OPEN, CommandStatus.PAYMENT_REQUESTED);

    private final CommandRepository commandRepository;

    public CommandService(CommandRepository commandRepository) {
        this.commandRepository = commandRepository;
    }

    public Command findByNumber(int number) {
        return commandRepository.findByNumber(number)
                .orElseThrow(() -> new CommandNotFoundException(number));
    }

    // Note: read-check-write without a locking strategy — two near-
    // simultaneous requests could both read AVAILABLE and both succeed in
    // opening the same command. Out of scope for this ticket (not
    // requested); worth revisiting (e.g. @Version optimistic locking) if
    // concurrent opens on the same number become a real concern.
    @Transactional
    public Command open(int number) {
        Command command = findByNumber(number);

        if (command.getStatus() != CommandStatus.AVAILABLE) {
            throw new CommandNotAvailableException(number, command.getStatus());
        }

        command.setStatus(CommandStatus.OPEN);
        return commandRepository.save(command);
    }

    // Same read-check-write caveat as open() above.
    @Transactional
    public Command close(int number) {
        Command command = findClosable(number);

        command.setStatus(CommandStatus.CLOSED);
        return commandRepository.save(command);
    }

    /**
     * Resolves a comanda ready to be closed but does <b>not</b> close it —
     * the status half of {@link #close(int)}'s precondition, extracted into
     * its own read-only method (FARELO-143) so a caller can validate "is
     * this comanda in a closable status" separately from "actually flip it
     * to {@code CLOSED}". Same read-only "resolve + validate, no save"
     * shape as {@link #findForPayment(int)}.
     *
     * <p>Exists specifically for {@code PaymentService#closeCommand}
     * (FARELO-143, "Validar total pago antes de fechar"): it needs to check
     * the <em>status</em> precondition before checking the <em>payment</em>
     * precondition (so a comanda that's e.g. still {@code AVAILABLE} fails
     * with {@link CommandCannotBeClosedException}, not a confusing payment
     * error, exactly as it did before this ticket), without duplicating
     * {@link #CLOSABLE_STATUSES} membership logic in the {@code payment}
     * package. {@link #close(int)} itself now delegates to this method for
     * that same check, then performs the actual transition+save — a
     * behavior-preserving refactor, not a change to what statuses are
     * accepted or which exception is thrown.
     *
     * @throws CommandNotFoundException {@code number} doesn't exist (via
     *     {@link #findByNumber}).
     * @throws CommandCannotBeClosedException the comanda exists but its
     *     status isn't in {@link #CLOSABLE_STATUSES}.
     */
    public Command findClosable(int number) {
        Command command = findByNumber(number);

        if (!CLOSABLE_STATUSES.contains(command.getStatus())) {
            throw new CommandCannotBeClosedException(number, command.getStatus());
        }

        return command;
    }

    /**
     * Fetches the command ready to accept a new order (FARELO-052/053).
     *
     * <p>Business decision: {@code AVAILABLE} and {@code OPEN} both accept
     * new orders. There is no explicit "open the command" step in the
     * customer-facing QR menu flow before ordering (prompt mestre seção 6)
     * — a customer scans the comanda and orders directly — so a command
     * still {@code AVAILABLE} transitions to {@code OPEN} as a side effect
     * of its first order, reusing {@link #open(int)}. A command already
     * {@code OPEN} (e.g. a second order in the same visit) is returned
     * as-is. {@code PAYMENT_REQUESTED}, {@code CLOSED} and {@code BLOCKED}
     * reject with {@link CommandCannotAcceptOrdersException} — none of
     * those make sense for placing a new order.
     */
    @Transactional
    public Command openForOrdering(int number) {
        Command command = findByNumber(number);

        return switch (command.getStatus()) {
            case AVAILABLE -> open(number);
            case OPEN -> command;
            case PAYMENT_REQUESTED, CLOSED, BLOCKED ->
                    throw new CommandCannotAcceptOrdersException(number, command.getStatus());
        };
    }

    /**
     * Resolves a comanda ready to have a payment recorded against it
     * (FARELO-141, {@code PaymentService#record}). Read-only, unlike {@link
     * #open}/{@link #close}/{@link #openForOrdering} above — recording a
     * payment is not itself a {@code Command} state transition (no future
     * ticket has defined one for it either; {@code PAYMENT_REQUESTED} is
     * reached some other way, not as a side effect of a payment), so this
     * method only validates and returns, never calls {@code save}.
     *
     * @throws CommandNotFoundException {@code number} doesn't exist (via
     *     {@link #findByNumber}).
     * @throws CommandCannotAcceptPaymentsException the comanda exists but its
     *     status isn't in {@link #PAYABLE_STATUSES} — see that constant's
     *     comment for the full reasoning.
     */
    public Command findForPayment(int number) {
        Command command = findByNumber(number);

        if (!PAYABLE_STATUSES.contains(command.getStatus())) {
            throw new CommandCannotAcceptPaymentsException(number, command.getStatus());
        }

        return command;
    }

    /**
     * Staff-facing edit of a comanda's central customer record
     * (FARELO-190/191, {@code PATCH /api/v1/commands/{number}/customer}).
     * No status precondition — unlike {@link #open}/{@link #close}, this
     * isn't a {@code Command} state transition and doesn't need the
     * comanda to be in any particular status; a cashier can correct a
     * customer's name/phone at any point in the comanda's lifecycle.
     *
     * <p>Full replace, same convention as every other {@code PUT}/{@code
     * PATCH} in this codebase that isn't itself a state transition:
     * omitting/blanking a field clears it (FARELO-190's "nome opcional",
     * FARELO-191's "permitir telefone vazio") rather than leaving the old
     * value untouched.
     */
    @Transactional
    public Command updateCustomer(int number, String customerName, String customerPhone) {
        Command command = findByNumber(number);
        applyCustomerInfo(command, customerName, customerPhone);
        return commandRepository.save(command);
    }

    /**
     * The write-through half of FARELO-191's "mesma informação central"
     * requirement: called from {@code OrderService#create} right after an
     * order is placed (from either the customer-facing QR checkout or the
     * PDV's own manual item entry, FARELO-182), so whichever channel last
     * supplied a real name/phone becomes this comanda's current customer
     * record — the same value {@link #updateCustomer} would produce by
     * hand. Unlike {@link #updateCustomer}, this is deliberately NOT a
     * full replace: a manually-entered PDV order (FARELO-182's flow, which
     * collects no customer info at all) must not blank out a name/phone a
     * customer already gave via the QR form earlier in the same visit — so
     * this only touches the command when at least one of the two arguments
     * is actually non-blank.
     *
     * <p>Takes the already-loaded {@code command} (not a number) — the
     * caller ({@code OrderService#create}) already holds the exact managed
     * entity it just resolved via {@code openForOrdering}; re-fetching by
     * number here would be a redundant query in the same transaction.
     */
    public void applyCustomerInfoIfProvided(Command command, String customerName, String customerPhone) {
        boolean hasName = customerName != null && !customerName.isBlank();
        boolean hasPhone = customerPhone != null && !customerPhone.isBlank();
        if (hasName || hasPhone) {
            applyCustomerInfo(command, customerName, customerPhone);
        }
    }

    private void applyCustomerInfo(Command command, String customerName, String customerPhone) {
        command.setCustomerName(
                customerName != null && !customerName.isBlank() ? customerName.trim() : null);
        command.setCustomerPhone(normalizePhone(customerPhone));
    }

    // FARELO-191: "normalizar número; armazenar código do país" — strips
    // everything but digits, then prepends Brazil's country code (55) when
    // the result looks like a bare local number (a 10 or 11-digit DDD +
    // number, the two real lengths for a Brazilian landline/mobile) and
    // doesn't already start with one. This is deliberately a heuristic,
    // not a real phone-number library (no libphonenumber dependency in
    // this codebase) — "validação básica" per the ticket, same
    // discipline-over-precision choice already made for
    // Order.customerPhone (see that field's javadoc: no format validator,
    // YAGNI) and CompanyFiscalConfiguration.cnpj (no digit-verification
    // logic). Returns null for blank/empty input — FARELO-191's "permitir
    // telefone vazio".
    static String normalizePhone(String rawPhone) {
        if (rawPhone == null) {
            return null;
        }
        String digits = rawPhone.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (!digits.startsWith("55") && (digits.length() == 10 || digits.length() == 11)) {
            digits = "55" + digits;
        }
        return digits;
    }

}
