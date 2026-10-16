package com.farelo.api.command;

import com.farelo.api.table.DiningTable;
import com.farelo.api.table.DiningTableService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
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

    // FARELO-315: a table counts as "occupied" while its linked comanda is
    // in active use — the same two statuses CLOSABLE_STATUSES/
    // PAYABLE_STATUSES above already settled on for "this comanda is a
    // live, ongoing visit" (OPEN, or PAYMENT_REQUESTED — staff marked it
    // awaiting payment, but the table hasn't been cleared yet). AVAILABLE
    // is excluded: a comanda that's AVAILABLE has no order/visit
    // happening, so its table (if any is still linked from a previous
    // cycle) isn't actually in use. CLOSED/BLOCKED are excluded for the
    // same reason they're excluded from PAYABLE_STATUSES.
    private static final Set<CommandStatus> OCCUPIED_TABLE_STATUSES =
            EnumSet.of(CommandStatus.OPEN, CommandStatus.PAYMENT_REQUESTED);

    private final CommandRepository commandRepository;
    private final DiningTableService diningTableService;

    public CommandService(CommandRepository commandRepository, DiningTableService diningTableService) {
        this.commandRepository = commandRepository;
        this.diningTableService = diningTableService;
    }

    public Command findByNumber(int number) {
        return commandRepository.findByNumber(number)
                .orElseThrow(() -> new CommandNotFoundException(number));
    }

    @Transactional(readOnly = true)
    public List<Command> listAll() {
        return commandRepository.findCurrentByOrderByNumberAsc();
    }

    @Transactional(readOnly = true)
    public List<Command> listVisits(int number) {
        List<Command> visits = commandRepository.findByNumberOrderByCycleNumberDesc(number);
        if (visits.isEmpty()) {
            throw new CommandNotFoundException(number);
        }
        return visits;
    }

    private Command findCurrentForUpdate(int number) {
        while (true) {
            Command locked = commandRepository.findFirstByNumberOrderByCycleNumberDesc(number)
                    .orElseThrow(() -> new CommandNotFoundException(number));
            // A concurrent reopen can insert the next cycle while this
            // request waits on the former latest row. Re-read, then lock
            // again if a newer row appeared instead of returning it unlocked.
            Command latest = findByNumber(number);
            if (latest.getId().equals(locked.getId())) {
                return locked;
            }
        }
    }

    /**
     * Holds the current visit while an order's possession proof and
     * idempotency key are checked. The caller's transaction must include the
     * eventual order creation so a close/reopen cannot switch visits between
     * authorization and persistence.
     */
    @Transactional
    public Command lockCurrentForOrder(int number) {
        return findCurrentForUpdate(number);
    }

    // Lock the current visit before changing it; the database also enforces
    // one active cycle and one row per (number, cycleNumber).
    @Transactional
    public Command open(int number) {
        Command command = findCurrentForUpdate(number);

        if (command.getStatus() == CommandStatus.CLOSED) {
            // A new UUID keeps every existing order/payment/discount/fiscal FK
            // bound to the closed visit; the new visit starts with no bill.
            Command next = new Command(number, command.getCycleNumber() + 1);
            next.setStatus(CommandStatus.OPEN);
            return commandRepository.saveAndFlush(next);
        }
        if (command.getStatus() != CommandStatus.AVAILABLE) {
            throw new CommandNotAvailableException(number, command.getStatus());
        }

        command.setStatus(CommandStatus.OPEN);
        return commandRepository.save(command);
    }

    // The row lock serializes close against new orders and payments.
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
    @Transactional
    public Command findClosable(int number) {
        Command command = findCurrentForUpdate(number);

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
        Command command = findCurrentForUpdate(number);

        return switch (command.getStatus()) {
            case AVAILABLE -> {
                command.setStatus(CommandStatus.OPEN);
                yield commandRepository.save(command);
            }
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
    @Transactional
    public Command findForPayment(int number) {
        Command command = findCurrentForUpdate(number);

        if (!PAYABLE_STATUSES.contains(command.getStatus())) {
            throw new CommandCannotAcceptPaymentsException(number, command.getStatus());
        }

        return command;
    }

    /** Locks the physical number and rejects writes through an older visit's UUID. */
    @Transactional
    public Command requireActiveVisit(Command original) {
        Command current = findCurrentForUpdate(original.getNumber());
        if (!current.getId().equals(original.getId()) || current.getStatus() == CommandStatus.CLOSED) {
            throw new CommandVisitNotCurrentException(original.getNumber(), original.getCycleNumber());
        }
        return current;
    }

    /**
     * Staff-facing edit of a comanda's central customer record
     * (FARELO-190/191, {@code PATCH /api/v1/commands/{number}/customer}).
     * The visit must still be current and not closed, so a correction
     * cannot rewrite customer data in the historical record.
     *
     * <p>Full replace, same convention as every other {@code PUT}/{@code
     * PATCH} in this codebase that isn't itself a state transition:
     * omitting/blanking a field clears it (FARELO-190's "nome opcional",
     * FARELO-191's "permitir telefone vazio") rather than leaving the old
     * value untouched.
     */
    @Transactional
    public Command updateCustomer(int number, String customerName, String customerPhone) {
        Command command = requireActiveVisit(findByNumber(number));
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

    /**
     * Staff-facing link/unlink of a comanda's optional {@link DiningTable}
     * (FARELO-315, {@code PATCH /api/v1/commands/{number}/table}). The
     * visit must still be current and not closed, like customer edits.
     *
     * <p>Full-replace semantics, same convention as {@link #updateCustomer}:
     * {@code tableNumber == null} clears the association ({@code
     * command.setTable(null)}) rather than leaving whatever was there
     * untouched — omitting the field on the request is how a caller
     * unlinks a table.
     *
     * @throws CommandNotFoundException {@code number} doesn't exist.
     * @throws com.farelo.api.table.DiningTableNotFoundException {@code
     *     tableNumber} is given but doesn't exist (via {@link
     *     DiningTableService#findByNumber}).
     */
    @Transactional
    public Command updateTable(int number, Integer tableNumber) {
        Command command = requireActiveVisit(findByNumber(number));
        DiningTable table = tableNumber == null ? null : diningTableService.findByNumber(tableNumber);
        command.setTable(table);
        return commandRepository.save(command);
    }

    /**
     * The "mesas ocupadas" half of FARELO-315's three occupancy indicators
     * ({@code GET /api/v1/tables/occupancy}) — the count of distinct
     * tables currently linked to a comanda in {@link
     * #OCCUPIED_TABLE_STATUSES}.
     *
     * <p>Lives here, in {@code command}, rather than in {@code
     * com.farelo.api.table.DiningTableService}, even though it's exposed
     * from {@code DiningTableController} — the same kind of cross-domain
     * placement decision {@code OrderService#getTotalOwed} (FARELO-143)
     * already made: this class already depends on {@code table} (for the
     * {@code Command.table} field and {@link #updateTable}), so adding
     * this one aggregate query here costs no new dependency direction.
     * Putting it in {@code table} instead would require {@code
     * DiningTableService} to depend on {@code CommandRepository}, which
     * would point the {@code table}↔{@code command} dependency both ways —
     * exactly the tangle {@code docs/domain-model.md} (line 24, "evitar
     * dependências cruzadas desnecessárias") and the FARELO-143 precedent
     * both steer away from. See {@code DiningTableService}'s javadoc for
     * the other half of this reasoning.
     */
    public long countOccupiedTables() {
        return commandRepository.countDistinctOccupiedTables(OCCUPIED_TABLE_STATUSES);
    }

}
