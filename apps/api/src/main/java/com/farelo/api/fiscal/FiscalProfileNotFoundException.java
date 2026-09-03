package com.farelo.api.fiscal;

import java.util.UUID;

/**
 * Thrown when an operation references a {@link FiscalProfile} id that does
 * not exist (e.g. fetching or updating a fiscal profile by an unknown id).
 * Same pattern as {@code com.farelo.api.inventory.IngredientNotFoundException}/
 * {@code com.farelo.api.catalog.CategoryNotFoundException}.
 */
public class FiscalProfileNotFoundException extends RuntimeException {

    private final UUID fiscalProfileId;

    public FiscalProfileNotFoundException(UUID fiscalProfileId) {
        super("FiscalProfile not found: " + fiscalProfileId);
        this.fiscalProfileId = fiscalProfileId;
    }

    public UUID getFiscalProfileId() {
        return fiscalProfileId;
    }

}
