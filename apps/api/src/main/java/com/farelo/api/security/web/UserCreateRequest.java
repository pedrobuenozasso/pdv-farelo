package com.farelo.api.security.web;

import com.farelo.api.security.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/users}. Never expose the JPA entity
 * directly on the API (see AGENTS.md) — this is the boundary DTO.
 *
 * <p>{@code password} travels here as plaintext (over HTTPS, per prompt
 * mestre seção 26 — see docs/domain-model.md) exactly once, on creation;
 * {@code UserService#create} hashes it before it ever reaches {@link
 * com.farelo.api.security.User}/the database, and it is never logged (see
 * {@link com.farelo.api.web.ApiExceptionHandler} — no handler here echoes
 * request bodies back).
 *
 * <p>{@code @Size(min = 8, max = 72)}: a floor against trivially-empty
 * passwords, and a ceiling matching BCrypt's own input limit — {@code
 * BCryptPasswordEncoder} rejects raw input over 72 bytes with an {@code
 * IllegalArgumentException}, so capping it here turns that into an ordinary
 * {@code 400 VALIDATION_ERROR} instead of an unhandled {@code 500}. No
 * further password strength policy (composition rules, breach lists, etc.)
 * — out of scope for this ticket, and arguably FARELO-121/122 territory if
 * ever needed.
 *
 * <p>No {@code active} field here, same as {@code CategoryRequest}/{@code
 * IngredientRequest} — a new user always starts {@code true} (see {@link
 * com.farelo.api.security.User}'s field default).
 */
public record UserCreateRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull UserRole role) {
}
