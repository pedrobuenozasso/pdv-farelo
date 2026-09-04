package com.farelo.api.command;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

@Service
public class CommandService {

    // Origin statuses from which a command can be closed. Both make
    // operational sense: a command can be closed straight from OPEN, or
    // after payment was requested (FARELO-034 — no payment/fiscal
    // validation yet, that's Epic 10/FARELO-143).
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
        Command command = findByNumber(number);

        if (!CLOSABLE_STATUSES.contains(command.getStatus())) {
            throw new CommandCannotBeClosedException(number, command.getStatus());
        }

        command.setStatus(CommandStatus.CLOSED);
        return commandRepository.save(command);
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

}
