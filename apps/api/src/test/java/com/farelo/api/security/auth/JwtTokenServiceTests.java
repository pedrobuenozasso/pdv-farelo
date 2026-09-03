package com.farelo.api.security.auth;

import com.farelo.api.security.User;
import com.farelo.api.security.UserRole;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtTokenService} — the standalone issue/verify
 * mechanics (FARELO-121), independent of {@code AuthenticationService}'s
 * credential-checking or any HTTP layer. Plain JUnit, no
 * {@code @SpringBootTest}/Postgres, same reasoning as
 * {@code WhatsAppCloudApiClientTests}: this is constructed directly (the
 * same way Spring would wire it via {@code @Value}), with no need for a
 * full application context to test pure token logic.
 */
class JwtTokenServiceTests {

    // 32+ bytes, satisfies HS256's minimum key-length check — same shape as
    // application.yml's dev default, just a distinct value so a test
    // failure can't be confused with a real deployment's secret leaking.
    private static final String SECRET = "test-only-jwt-signing-secret-not-used-anywhere-else-1234567890";

    private final User user = new User("Ana Souza", "ana@farelo.dev", "irrelevant-hash", UserRole.MANAGER);

    @Test
    void issuedTokenCanBeParsedBackToTheSameIdentity() {
        setUserId(user);
        JwtTokenService jwtTokenService = new JwtTokenService(SECRET, 60);

        IssuedToken issuedToken = jwtTokenService.issue(user);
        AuthenticatedPrincipal principal = jwtTokenService.parse(issuedToken.token());

        assertThat(principal.userId()).isEqualTo(user.getId());
        assertThat(principal.email()).isEqualTo("ana@farelo.dev");
        assertThat(principal.role()).isEqualTo(UserRole.MANAGER);
    }

    @Test
    void issuedTokenExpiresAtRoughlyNowPlusConfiguredMinutes() {
        setUserId(user);
        JwtTokenService jwtTokenService = new JwtTokenService(SECRET, 60);

        OffsetDateTime before = OffsetDateTime.now().plusMinutes(60).minusSeconds(5);
        IssuedToken issuedToken = jwtTokenService.issue(user);
        OffsetDateTime after = OffsetDateTime.now().plusMinutes(60).plusSeconds(5);

        assertThat(issuedToken.expiresAt()).isBetween(before, after);
    }

    @Test
    void rejectsATokenSignedWithADifferentSecret() {
        setUserId(user);
        JwtTokenService issuer = new JwtTokenService(SECRET, 60);
        JwtTokenService verifierWithDifferentSecret =
                new JwtTokenService("a-completely-different-secret-also-32-bytes-plus", 60);

        IssuedToken issuedToken = issuer.issue(user);

        assertThatThrownBy(() -> verifierWithDifferentSecret.parse(issuedToken.token()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsAMalformedToken() {
        JwtTokenService jwtTokenService = new JwtTokenService(SECRET, 60);

        assertThatThrownBy(() -> jwtTokenService.parse("not-a-real-jwt"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsAnAlreadyExpiredToken() {
        setUserId(user);
        // Negative expiration: issue() adds this to "now", so the token's
        // exp claim lands in the past the instant it's minted — the
        // simplest way to get a real, correctly-signed-but-expired token
        // without sleeping the test thread.
        JwtTokenService jwtTokenService = new JwtTokenService(SECRET, -1);

        IssuedToken issuedToken = jwtTokenService.issue(user);

        assertThatThrownBy(() -> jwtTokenService.parse(issuedToken.token()))
                .isInstanceOf(InvalidTokenException.class);
    }

    // User#id is only ever populated by Hibernate's @UuidGenerator on
    // persist — there is no setter (by design, see User's javadoc: ids are
    // never assigned by application code) and this test deliberately stays
    // plain-JUnit/no-database (see class javadoc), so there is no INSERT to
    // populate it the normal way. Reflection is the narrow workaround: it
    // only stands in for "this row was already persisted", which is exactly
    // the precondition issue()/parse() actually require (User#getId() must
    // be non-null) — nothing here tests id generation itself, that's
    // already covered by e.g. UserControllerIntegrationTests.
    private static void setUserId(User user) {
        try {
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, java.util.UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

}
