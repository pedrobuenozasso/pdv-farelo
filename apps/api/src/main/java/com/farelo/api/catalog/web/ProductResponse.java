package com.farelo.api.catalog.web;

import com.farelo.api.catalog.Product;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body exposing only the public fields of {@link Product} — the
 * JPA entity itself is never returned by the API (see AGENTS.md).
 */
public record ProductResponse(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        boolean active,
        UUID categoryId,
        String imageUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.isActive(),
                product.getCategory().getId(),
                product.getImageUrl(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

}
