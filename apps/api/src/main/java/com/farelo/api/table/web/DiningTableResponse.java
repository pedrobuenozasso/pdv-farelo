package com.farelo.api.table.web;

import com.farelo.api.table.DiningTable;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body exposing only the public fields of {@link DiningTable} —
 * the JPA entity itself is never returned by the API (see AGENTS.md).
 */
public record DiningTableResponse(
        UUID id,
        int number,
        int capacity,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static DiningTableResponse from(DiningTable table) {
        return new DiningTableResponse(
                table.getId(),
                table.getNumber(),
                table.getCapacity(),
                table.getCreatedAt(),
                table.getUpdatedAt());
    }

}
