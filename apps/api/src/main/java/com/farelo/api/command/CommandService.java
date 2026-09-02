package com.farelo.api.command;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommandService {

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

}
