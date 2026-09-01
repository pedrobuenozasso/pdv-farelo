package com.farelo.api.catalog;

import java.util.UUID;

/**
 * Thrown when an operation references a {@link Product} id that does not
 * exist (e.g. updating a product by an unknown id). Same pattern as
 * {@link CategoryNotFoundException}.
 */
public class ProductNotFoundException extends RuntimeException {

    private final UUID productId;

    public ProductNotFoundException(UUID productId) {
        super("Product not found: " + productId);
        this.productId = productId;
    }

    public UUID getProductId() {
        return productId;
    }

}
