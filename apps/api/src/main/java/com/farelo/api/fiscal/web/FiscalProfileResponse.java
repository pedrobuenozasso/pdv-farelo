package com.farelo.api.fiscal.web;

import com.farelo.api.fiscal.FiscalProfile;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body exposing only the public fields of {@link FiscalProfile} —
 * the JPA entity itself is never returned by the API (see AGENTS.md).
 *
 * <p>No NCM/CFOP/CST/CSOSN (or any other prompt-mestre-seção-24 fiscal
 * code) here — none of those fields exist on {@link FiscalProfile} yet, see
 * that class's javadoc for why (FARELO-152/153/154, future tickets).
 */
public record FiscalProfileResponse(
        UUID id,
        String name,
        String description,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static FiscalProfileResponse from(FiscalProfile fiscalProfile) {
        return new FiscalProfileResponse(
                fiscalProfile.getId(),
                fiscalProfile.getName(),
                fiscalProfile.getDescription(),
                fiscalProfile.isActive(),
                fiscalProfile.getCreatedAt(),
                fiscalProfile.getUpdatedAt());
    }

}
