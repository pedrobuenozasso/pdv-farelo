package com.farelo.api.catalog.web;

import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductionStation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body exposing only the public fields of {@link Product} — the
 * JPA entity itself is never returned by the API (see AGENTS.md).
 *
 * <p>{@code productionStation} (FARELO-073) serializes as {@code null} when
 * a product has no station assigned yet — same as
 * {@link com.farelo.api.catalog.Product}'s field, not a placeholder for "not
 * sent".
 *
 * <p>{@code fiscalProfileId} (FARELO-151) exposes just the id, same shape as
 * {@code categoryId} — no fiscal profile name/description here. Reading
 * {@code product.getFiscalProfile().getId()} is safe on an uninitialized
 * lazy proxy (the id is already known to the proxy without triggering a DB
 * hit/session requirement), same established lesson already relied upon for
 * {@code categoryId} below — so no {@code JOIN FETCH} is needed on whichever
 * repository query backs this response, unlike cases elsewhere in this
 * codebase that read a *name* off a lazy association (e.g. {@code
 * PrintJobRepository#findByStatusOrderByCreatedAtAsc}'s {@code JOIN FETCH
 * p.order}).
 */
public record ProductResponse(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        boolean active,
        boolean availableOnMenu,
        boolean availableOnPos,
        UUID categoryId,
        String imageUrl,
        ProductionStation productionStation,
        UUID fiscalProfileId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.isActive(),
                product.isAvailableOnMenu(),
                product.isAvailableOnPos(),
                product.getCategory().getId(),
                product.getImageUrl(),
                product.getProductionStation(),
                product.getFiscalProfile() != null ? product.getFiscalProfile().getId() : null,
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

}
