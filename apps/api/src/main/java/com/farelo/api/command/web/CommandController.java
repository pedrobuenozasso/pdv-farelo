package com.farelo.api.command.web;

import com.farelo.api.command.Command;
import com.farelo.api.command.CommandService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commands")
public class CommandController {

    private final CommandService commandService;

    public CommandController(CommandService commandService) {
        this.commandService = commandService;
    }

    // {number} is the human-facing business identifier (1-100), never the
    // technical UUID id — see Command's javadoc / prompt mestre seção 41.
    @GetMapping("/{number}")
    public CommandResponse findByNumber(@PathVariable int number) {
        Command command = commandService.findByNumber(number);
        return CommandResponse.from(command);
    }

    // POST, not PATCH: /open is an action ("open this command"), not a
    // partial update of the resource's representation — a pragmatic REST
    // convention for state-transition endpoints (verb suffix + POST), and
    // it sidesteps PATCH's usual implication of a body describing the
    // partial change (e.g. JSON Patch/Merge Patch), which this endpoint
    // doesn't have.
    @PostMapping("/{number}/open")
    public CommandResponse open(@PathVariable int number) {
        Command command = commandService.open(number);
        return CommandResponse.from(command);
    }

}
