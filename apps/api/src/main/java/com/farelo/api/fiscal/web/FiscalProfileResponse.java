package com.farelo.api.fiscal.web;

import com.farelo.api.fiscal.FiscalProfile;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body exposing only the public fields of {@link FiscalProfile} —
 * the JPA entity itself is never returned by the API (see AGENTS.md).
 *
 * <p>{@code ncm} (FARELO-152) is the first of the prompt-mestre-seção-24
 * fiscal codes to exist on {@link FiscalProfile} — {@code null} means "not
 * configured yet" (see {@code FiscalProfile.ncm}'s javadoc). {@code cfop}
 * (FARELO-153) is the second, same {@code null}-means-"not configured yet"
 * shape (see {@code FiscalProfile.cfop}'s javadoc). CST/CSOSN (or any other
 * seção 24 fiscal code) still doesn't exist on {@link FiscalProfile}, see
 * that class's javadoc for why (FARELO-154, future ticket).
 */
public record FiscalProfileResponse(
        UUID id,
        String name,
        String description,
        String ncm,
        String cfop,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static FiscalProfileResponse from(FiscalProfile fiscalProfile) {
        return new FiscalProfileResponse(
                fiscalProfile.getId(),
                fiscalProfile.getName(),
                fiscalProfile.getDescription(),
                fiscalProfile.getNcm(),
                fiscalProfile.getCfop(),
                fiscalProfile.isActive(),
                fiscalProfile.getCreatedAt(),
                fiscalProfile.getUpdatedAt());
    }

}
