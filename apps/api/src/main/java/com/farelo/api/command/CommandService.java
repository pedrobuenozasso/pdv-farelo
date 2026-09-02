package com.farelo.api.command;

import org.springframework.stereotype.Service;

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

}
