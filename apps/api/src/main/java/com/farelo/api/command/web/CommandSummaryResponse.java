package com.farelo.api.command.web;

import com.farelo.api.command.Command;
import com.farelo.api.command.CommandStatus;

/** Minimal staff-only data for the PDV's comanda grid. */
public record CommandSummaryResponse(int number, CommandStatus status, Integer tableNumber) {

    public static CommandSummaryResponse from(Command command) {
        return new CommandSummaryResponse(
                command.getNumber(),
                command.getStatus(),
                command.getTable() == null ? null : command.getTable().getNumber());
    }
}
