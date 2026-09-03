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
 * <p><b>Not wired into {@code ApiExceptionHandler} yet</b>: nothing in this
 * ticket calls {@code parse} from an HTTP request path — no endpoint
 * requires a token (FARELO-121's scope is issuance/verification only, not
 * protecting anything; see {@link JwtTokenService}'s javadoc). Deciding this
 * exception's HTTP status/error code now would mean pre-deciding
 * FARELO-123/124's request-authentication design, which those tickets
 * haven't specified yet — same reasoning {@code PasswordEncoderConfig}
 * already applied to avoid pre-deciding FARELO-121/123 inside FARELO-120.
 * Today this class is only exercised directly by
 * {@code JwtTokenServiceTests} (unit tests of the standalone verifier).
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }

}
