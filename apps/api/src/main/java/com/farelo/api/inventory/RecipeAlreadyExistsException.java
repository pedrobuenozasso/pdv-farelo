package com.farelo.api.inventory;

import java.util.UUID;

/**
 * Thrown when creating a {@link Recipe} for a product that already has an
 * active one — see {@link Recipe}'s javadoc for why "at most one active
 * recipe per product" is the rule and how it's enforced (service-layer
 * check here, backed by a partial unique index at the DB level as the real
 * source of truth). 409 Conflict, same reasoning/shape as
 * {@code com.farelo.api.command.CommandNotAvailableException}/
 * {@code com.farelo.api.printing.PrintJobInvalidTransitionException}: the
 * request is well-formed, but conflicts with existing state.
 */
public class RecipeAlreadyExistsException extends RuntimeException {

    private final UUID productId;

    public RecipeAlreadyExistsException(UUID productId) {
        super("Product already has an active recipe: " + productId);
        this.productId = productId;
    }

    public UUID getProductId() {
        return productId;
    }

}
