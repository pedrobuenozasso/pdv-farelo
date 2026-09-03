package com.farelo.api.security.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/auth/login} (FARELO-121). Never
 * expose the JPA entity directly on the API (see AGENTS.md) — this is the
 * boundary DTO.
 *
 * <p><b>Deliberately just {@code @NotBlank}, no {@code @Email} on
 * {@code email} and no {@code @Size} on {@code password}</b> — contrast with
 * {@code UserCreateRequest}, which validates both, since it's an admin
 * creating an account, not a login attempt. Here, adding either would split
 * "these credentials don't work" into two different responses: a
 * malformed-looking email or too-short password would 400
 * {@code VALIDATION_ERROR} instead of 401 {@code INVALID_CREDENTIALS} for
 * exactly the same underlying fact (this can never be a valid login). That
 * split costs nothing to an attacker probing for real accounts (email
 * format/password length aren't secrets), but it's one fewer distinct
 * response shape to reason about for a request whose entire design goal is
 * "every kind of wrong looks the same" (see
 * {@code com.farelo.api.security.InvalidCredentialsException}'s javadoc) —
 * so blank/missing fields are the only thing validated here, and everything
 * else funnels through {@link com.farelo.api.security.AuthenticationService#login}'s
 * one generic failure path.
 */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {
}
