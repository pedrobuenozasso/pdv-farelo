package com.farelo.api.security;

import java.util.UUID;

/**
 * Thrown when an operation references a {@link User} id that does not
 * exist. Same pattern as {@code com.farelo.api.inventory.IngredientNotFoundException}/
 * {@code com.farelo.api.catalog.CategoryNotFoundException}.
 */
public class UserNotFoundException extends RuntimeException {

    private final UUID userId;

    public UserNotFoundException(UUID userId) {
        super("User not found: " + userId);
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }

}
