package com.farelo.api.command.web;

import com.farelo.api.command.Command;
import com.farelo.api.command.CommandStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body exposing only the public fields of {@link Command} — the
 * JPA entity itself is never returned by the API (see AGENTS.md).
 */
public record CommandResponse(
        UUID id,
        int number,
        CommandStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static CommandResponse from(Command command) {
        return new CommandResponse(
                command.getId(),
                command.getNumber(),
                command.getStatus(),
                command.getCreatedAt(),
                command.getUpdatedAt());
    }

}
