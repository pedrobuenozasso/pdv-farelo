package com.farelo.api.catalog.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/products}. Never expose the JPA
 * entity directly on the API (see AGENTS.md) — this is the boundary DTO.
 */
public record ProductRequest(
        @NotBlank String name,
        String description,
        @NotNull @DecimalMin(value = "0.00", inclusive = true) BigDecimal price,
        @NotNull UUID categoryId,
        String imageUrl) {
}
