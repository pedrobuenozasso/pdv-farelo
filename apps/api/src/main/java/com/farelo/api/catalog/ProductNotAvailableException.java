package com.farelo.api.catalog;

import java.util.UUID;

/**
 * Thrown when an operation requires a {@link Product} to be
 * {@code active} (sellable) but it currently isn't (e.g. adding an
 * inactive product to an order — see {@code com.farelo.api.ordering}).
 */
public class ProductNotAvailableException extends RuntimeException {

    private final UUID productId;

    public ProductNotAvailableException(UUID productId) {
        super("Product not available: " + productId);
        this.productId = productId;
    }

    public UUID getProductId() {
        return productId;
    }

}
