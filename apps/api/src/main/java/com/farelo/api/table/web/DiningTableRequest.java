package com.farelo.api.table.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for {@code POST /api/v1/tables}. Never expose the JPA
 * entity directly on the API (see AGENTS.md) — this is the boundary DTO.
 */
public record DiningTableRequest(
        @NotNull(message = "number é obrigatório") @Positive(message = "number deve ser positivo") Integer number,
        @NotNull(message = "capacity é obrigatório") @Positive(message = "capacity deve ser positivo") Integer capacity) {
}
