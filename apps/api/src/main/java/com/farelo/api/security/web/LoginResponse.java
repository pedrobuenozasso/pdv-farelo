package com.farelo.api.security.web;

import com.farelo.api.security.auth.IssuedToken;

import java.time.OffsetDateTime;

/**
 * Response body for {@code POST /api/v1/auth/login} (FARELO-121). Carries
 * only the token and its expiry — never the {@link com.farelo.api.security.User}
 * itself (name/email/role/id), let alone {@code passwordHash}, same
 * "never leak more than the boundary needs" reasoning as
 * {@code UserResponse}'s javadoc. A caller who needs their own profile can
 * decode the JWT's claims client-side or, once it exists, call a future
 * {@code GET /api/v1/users/me}-shaped endpoint — not this ticket's scope.
 */
public record LoginResponse(String token, OffsetDateTime expiresAt) {

    public static LoginResponse from(IssuedToken issuedToken) {
        return new LoginResponse(issuedToken.token(), issuedToken.expiresAt());
    }

}
