package com.farelo.api.catalog.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/categories}. Never expose the JPA
 * entity directly on the API (see AGENTS.md) — this is the boundary DTO.
 */
public record CategoryRequest(@NotBlank String name) {
}
