package com.farelo.api.security.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/v1/users/{id}/password}. See {@code
 * UserService#updatePassword}'s javadoc for why this is a separate
 * endpoint/DTO from the general profile update ({@link UserUpdateRequest})
 * and why it doesn't ask for the current password yet.
 *
 * <p>Same {@code @Size(min = 8, max = 72)} reasoning as {@link
 * UserCreateRequest#password()}.
 */
public record UserPasswordUpdateRequest(
        @NotBlank @Size(min = 8, max = 72) String newPassword) {
}
