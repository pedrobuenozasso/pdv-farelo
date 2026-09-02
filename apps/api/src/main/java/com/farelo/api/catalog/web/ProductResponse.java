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
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

}
