package com.farelo.api.catalog.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/categories}. Never expose the JPA
 * entity directly on the API (see AGENTS.md) — this is the boundary DTO.
 *
 * <p>{@code description} (FARELO-261) is optional, same convention as
 * {@code ProductRequest#description()}. {@code sortOrder} is optional too
 * — {@code null} means "use the default" ({@code 0}, see {@code
 * CategoryService#create}), same wrapper-type reasoning {@code
 * ProductRequest#availableOnMenu()}'s javadoc already established for
 * distinguishing "not sent" from an explicit value, except here the
 * default itself ({@code 0}) is safe to apply either way — a category
 * with no stated order sorting alongside every other unset one is exactly
 * the right behavior, not a footgun like a silently-inactive product would
 * be.
 */
public record CategoryRequest(@NotBlank String name, String description, Integer sortOrder) {
}
