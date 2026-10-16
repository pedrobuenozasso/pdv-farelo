package com.farelo.api.command.web;

import com.farelo.api.command.Command;
import com.farelo.api.command.CommandStatus;

/** Minimal information required by the unauthenticated QR menu. */
public record CommandPublicResponse(int number, CommandStatus status) {

    public static CommandPublicResponse from(Command command) {
        return new CommandPublicResponse(command.getNumber(), command.getStatus());
    }
}
