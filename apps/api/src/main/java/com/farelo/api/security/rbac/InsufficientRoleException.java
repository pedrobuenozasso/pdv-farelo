package com.farelo.api.security.rbac;

import com.farelo.api.security.UserRole;

/**
 * Thrown by {@link RoleAuthorizationInterceptor} when a request carries a
 * valid, authenticated {@link com.farelo.api.security.auth.AuthenticatedPrincipal}
 * (signature and expiry already checked out — see
 * {@link com.farelo.api.security.auth.JwtTokenService#parse}) whose
 * {@link UserRole} is not one of the calling handler's
 * {@link RequireRole#value()}.
 *
 * <p>Deliberately a distinct type/status from
 * {@link com.farelo.api.security.auth.InvalidTokenException} (401): that one
 * means "I don't know who you are" (no token, malformed token, bad
 * signature, expired); this one means "I know exactly who you are, and
 * you're not allowed to do this" — the standard 401-vs-403 distinction.
 * Mapped to {@code 403 Forbidden} / {@code FORBIDDEN} in
 * {@code ApiExceptionHandler}.
 *
 * <p>The message intentionally does <b>not</b> list which roles would have
 * been allowed — that's a detail about the endpoint's policy, not something
 * an unauthorized caller needs to learn from the error response (same
 * "don't leak more than necessary" instinct behind
 * {@code InvalidCredentialsException}'s single generic message, though the
 * reasoning here is narrower: the caller's own identity/role is not a
 * secret, unlike login enumeration).
 */
public class InsufficientRoleException extends RuntimeException {

    public InsufficientRoleException(UserRole callerRole) {
        super("Role " + callerRole + " is not allowed to perform this operation");
    }

}
