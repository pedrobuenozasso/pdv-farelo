package com.farelo.api.cashier.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/shifts} (FARELO-306). Never expose
 * the JPA entity directly on the API (see AGENTS.md) — this is the boundary
 * DTO.
 */
public record ShiftRequest(
        @NotBlank String name,
        @NotBlank String openingAccountName,
        @NotBlank String closingAccountName) {
}
