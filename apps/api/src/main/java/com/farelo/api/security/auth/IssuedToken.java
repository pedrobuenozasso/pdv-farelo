package com.farelo.api.security.auth;

import java.time.OffsetDateTime;

/**
 * A freshly issued JWT (FARELO-121) — the internal result of
 * {@link JwtTokenService#issue}, before it's shaped into the API's
 * {@code LoginResponse} DTO (see {@code com.farelo.api.security.web}). Kept
 * as its own type here, not directly returned as the web DTO, for the same
 * reason every other service in this codebase returns a domain-ish object
 * that the {@code web} layer maps rather than a boundary DTO itself (see
 * AGENTS.md) — {@link com.farelo.api.security.AuthenticationService}, this
 * record's only producer, has no reason to depend on the {@code web}
 * package.
 *
 * @param token     the compact JWS string the client sends back as
 *                  {@code Authorization: Bearer <token>} on future requests
 *                  (once FARELO-123/124 actually start requiring it — this
 *                  ticket only issues/validates it).
 * @param expiresAt UTC instant the token stops being valid — see
 *                  {@link JwtTokenService}'s javadoc for the expiry
 *                  decision.
 */
public record IssuedToken(String token, OffsetDateTime expiresAt) {
}
