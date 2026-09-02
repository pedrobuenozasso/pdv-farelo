package com.farelo.api.security.web;

import com.farelo.api.security.User;
import com.farelo.api.security.UserRole;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body exposing only the public fields of {@link User} — the JPA
 * entity itself is never returned by the API (see AGENTS.md).
 *
 * <p><b>{@code passwordHash} is deliberately absent</b>, without exception,
 * from every response this controller returns (create, list, get, update,
 * password change) — this is the one field of {@link User} this record must
 * never carry, since {@link #from(User)} is the single place every
 * controller method in {@code UserController} builds its response from.
 */
public record UserResponse(
        UUID id,
        String name,
        String email,
        UserRole role,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

}
