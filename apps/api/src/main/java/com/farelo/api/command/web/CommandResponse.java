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
        String customerName,
        String customerPhone,
        Integer tableNumber,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    // FARELO-315: tableNumber is the DiningTable's own business number
    // (null when no table is linked), not its technical UUID id — same
    // "never expose the internal id as a business identifier" convention
    // as Command.number itself.
    public static CommandResponse from(Command command) {
        return new CommandResponse(
                command.getId(),
                command.getNumber(),
                command.getStatus(),
                command.getCustomerName(),
                command.getCustomerPhone(),
                command.getTable() == null ? null : command.getTable().getNumber(),
                command.getCreatedAt(),
                command.getUpdatedAt());
    }

}
