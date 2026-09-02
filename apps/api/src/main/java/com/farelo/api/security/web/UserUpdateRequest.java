package com.farelo.api.security.web;

import com.farelo.api.security.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PUT /api/v1/users/{id}} — full profile replace
 * (name/email/role/active). Deliberately excludes the password (see {@link
 * UserPasswordUpdateRequest}/{@code UserService#updatePassword} for why
 * that's a separate endpoint).
 *
 * <p>Deliberately a separate record from {@link UserCreateRequest}, same
 * reasoning as {@code IngredientUpdateRequest} vs {@code IngredientRequest}:
 * {@code PUT} is a full replace and needs {@code active} to be settable, but
 * {@code active} doesn't belong on creation (it always starts {@code true}).
 * {@code active} is {@code Boolean} (wrapper, {@code @NotNull}) rather than
 * a primitive {@code boolean} to force the client to send it explicitly —
 * Jackson would otherwise silently default a missing primitive field on a
 * record to {@code false}, deactivating the user whenever a client omits
 * it.
 */
public record UserUpdateRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotNull UserRole role,
        @NotNull Boolean active) {
}
