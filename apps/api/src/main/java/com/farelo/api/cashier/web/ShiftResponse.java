package com.farelo.api.cashier.web;

import com.farelo.api.cashier.Shift;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body exposing only the public fields of {@link Shift} — the JPA
 * entity itself is never returned by the API (see AGENTS.md).
 */
public record ShiftResponse(
        UUID id,
        String name,
        String openingAccountName,
        String closingAccountName,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static ShiftResponse from(Shift shift) {
        return new ShiftResponse(
                shift.getId(),
                shift.getName(),
                shift.getOpeningAccountName(),
                shift.getClosingAccountName(),
                shift.isActive(),
                shift.getCreatedAt(),
                shift.getUpdatedAt());
    }

}
