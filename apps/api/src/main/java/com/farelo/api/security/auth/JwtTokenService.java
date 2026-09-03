package com.farelo.api.security.auth;

import com.farelo.api.security.User;
import com.farelo.api.security.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and verifies the JWT (FARELO-121) returned by
 * {@code POST /api/v1/auth/login} — the only piece of "authentication" this
 * ticket builds: prove who you are once (email+password, checked by
 * {@code AuthenticationService}), then carry a self-contained, tamper-proof
 * token on future requests instead of resending credentials. See
 * {@code AuthenticationService}'s javadoc for the credential-checking half;
 * this class only knows how to mint/read the token itself.
 *
 * <h2>Format — signed JWT (JWS), not an opaque/random token</h2>
 *
 * Two shapes were on the table: (a) a random opaque string, persisted in a
 * new {@code auth_token}/{@code session} table and looked up on every
 * request, or (b) a self-contained signed JWT, verified locally with no
 * database round-trip. <b>Decision: (b), a JWT.</b> Reasons:
 * <ul>
 *   <li>No new table/migration for a ticket whose own instructions are to
 *   avoid pre-deciding FARELO-122/123/124's design — a session table shape
 *   (what it stores, whether it supports multiple concurrent sessions per
 *   user, how it's pruned) is exactly the kind of decision better made by
 *   whichever future ticket first needs revocation (see "What this
 *   deliberately does NOT do" below), not guessed at here.</li>
 *   <li>Verification (FARELO-123/124's future request filter calling
 *   {@link #parse}) never touches the database — it's a pure signature +
 *   expiry check. That keeps this ticket's token machinery fully decoupled
 *   from request-handling infrastructure that doesn't exist yet, and means
 *   whatever FARELO-123/124 builds (a servlet {@code Filter}, a
 *   {@code HandlerInterceptor}, an argument resolver — their call) only
 *   needs this one pure function, not a repository/service dependency.</li>
 * </ul>
 *
 * <h2>Signing — HMAC-SHA256 (HS256), one shared secret</h2>
 *
 * A symmetric algorithm (one secret both signs and verifies) over an
 * asymmetric one (e.g. RS256, a private/public keypair): this is a single
 * backend service issuing and verifying its own tokens — nothing else in
 * this system (no separate service, no third party) ever needs to verify a
 * Farelo token without also being trusted to have the secret, so the extra
 * complexity of key-pair management buys nothing here. Revisit only if a
 * future ticket needs a separate service to verify tokens it didn't issue.
 * The secret ({@code security.jwt.secret}, {@code application.yml}) is read
 * from an environment variable the same {@code ${ENV_VAR:default}} way as
 * {@code spring.datasource.password}/{@code whatsapp.api.access-token} —
 * never hardcoded, never logged. The default in {@code application.yml} is
 * explicitly dev-only/insecure (same spirit as {@code
 * spring.datasource.password}'s {@code change-me} default) and long enough
 * to satisfy {@link Keys#hmacShaKeyFor}'s minimum key-length check for
 * HS256 (256 bits / 32 bytes) — a real deployment must override it via
 * {@code JWT_SECRET}.
 *
 * <h2>Expiry — yes, configurable, defaulting to 8 hours</h2>
 *
 * A token that never expires would mean a leaked/stolen token (or one
 * belonging to an employee who left, see {@link User#isActive()}) stays
 * valid forever, with no story for shutting it off short of rotating the
 * signing secret for every user at once. {@code security.jwt.expiration-minutes}
 * (default {@code 480} = 8 hours, one work shift) bounds that exposure
 * window without requiring the client to re-login mid-shift. Configurable
 * rather than a literal in code, same reasoning as {@code
 * outbox.worker.poll-interval-ms}.
 *
 * <h2>What this deliberately does NOT do</h2>
 *
 * <b>No revocation/logout, no refresh tokens, no "list my active
 * sessions"</b> — a stateless JWT can't be invalidated before its own
 * {@code exp} without either a server-side blocklist (which reintroduces
 * the per-request database lookup this design chose to avoid, undermining
 * the whole point of (b) above) or short-lived tokens + a separate refresh
 * flow (real added complexity, and nothing in this ticket's scope —
 * "login + token issuance/validation only" — asks for a refresh flow). The
 * 8-hour default expiry is the whole mitigation for now: worse than instant
 * revocation, acceptable for a first authentication ticket where no
 * endpoint even checks the token yet (FARELO-123/124). Revisit if/when a
 * real "log this device out now" requirement shows up.
 */
@Component
public class JwtTokenService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey signingKey;
    private final Duration expiration;

    public JwtTokenService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expiration-minutes:480}") long expirationMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    /**
     * Mints a token for {@code user} — {@code sub} is the user's id,
     * {@code email}/{@code role} are carried as custom claims (a snapshot at
     * issuance time, see {@link AuthenticatedPrincipal}'s javadoc), signed
     * HS256 with {@link #signingKey}, expiring after {@link #expiration}.
     */
    public IssuedToken issue(User user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(expiration);

        String token = Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();

        return new IssuedToken(token, expiresAt.atOffset(ZoneOffset.UTC));
    }

    /**
     * Verifies {@code token}'s signature and expiry, and returns the
     * identity it claims. Any failure — bad signature (tampered or signed
     * with a different secret), malformed compact JWS, or an {@code exp} in
     * the past — is collapsed into {@link InvalidTokenException} (see that
     * class's javadoc for why one type, and why this isn't wired into
     * {@code ApiExceptionHandler} yet).
     */
    public AuthenticatedPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return new AuthenticatedPrincipal(
                    UUID.fromString(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    UserRole.valueOf(claims.get(CLAIM_ROLE, String.class)));
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException("Invalid or expired token", ex);
        }
    }

}
