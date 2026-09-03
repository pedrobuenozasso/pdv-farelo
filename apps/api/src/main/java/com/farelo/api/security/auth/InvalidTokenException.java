package com.farelo.api.security.auth;

/**
 * Thrown by {@link JwtTokenService#parse} when a token fails to validate —
 * bad signature, malformed compact JWS, or expired ({@code exp} in the
 * past). Wraps whichever specific {@code io.jsonwebtoken} exception jjwt
 * raised (see {@link JwtTokenService#parse}'s javadoc) so callers only ever
 * need to handle this one type, the same "collapse every failure mode of an
 * external/library boundary into one project exception" reasoning already
 * applied by {@code WhatsAppCloudApiClient} for {@code RestClientException}.
 *
 * <p><b>Wired into {@code ApiExceptionHandler} as of FARELO-122</b> —
 * {@code 401 Unauthorized} / {@code UNAUTHENTICATED} — thrown by
 * {@code com.farelo.api.security.rbac.RoleAuthorizationInterceptor} both
 * when {@link #parse} rejects a presented token and when no
 * {@code Authorization} header is presented at all (the interceptor throws
 * this same type for "no token"; see that class's javadoc for the full
 * pipeline). This exception type itself is unchanged from FARELO-121 — only
 * where it's caught changed. Reachable only through FARELO-122's own
 * dedicated test controller until FARELO-123, the first ticket to annotate
 * a real production controller with {@code @RequireRole} (the Admin
 * surface — {@code CategoryController}/{@code ProductController}/
 * {@code UserController}); FARELO-124 (PDV/kitchen) is still a future
 * ticket.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }

}
