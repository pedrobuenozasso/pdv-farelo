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

}
