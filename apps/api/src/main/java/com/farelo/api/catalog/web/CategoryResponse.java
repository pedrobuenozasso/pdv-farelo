package com.farelo.api.catalog.web;

import com.farelo.api.catalog.Category;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body exposing only the public fields of {@link Category} — the
 * JPA entity itself is never returned by the API (see AGENTS.md).
 */
public record CategoryResponse(
        UUID id,
        String name,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.isActive(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }

}
