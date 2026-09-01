package com.farelo.api.catalog;

import java.util.UUID;

/**
 * Thrown when an operation references a {@link Category} id that does not
 * exist (e.g. creating a {@link Product} with an unknown {@code categoryId}).
 */
public class CategoryNotFoundException extends RuntimeException {

    private final UUID categoryId;

    public CategoryNotFoundException(UUID categoryId) {
        super("Category not found: " + categoryId);
        this.categoryId = categoryId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

}
