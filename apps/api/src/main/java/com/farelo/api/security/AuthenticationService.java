package com.farelo.api.security;

import com.farelo.api.security.auth.IssuedToken;
import com.farelo.api.security.auth.JwtTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Application service backing {@code POST /api/v1/auth/login} (FARELO-121)
 * — the credential-checking half of authentication. {@link JwtTokenService}
 * (the {@code security.auth} sub-package) owns the token itself; this class
 * only decides <i>whether</i> a token gets issued at all.
 *
 * <p>Reuses {@link UserRepository#findByEmail} (already exposed for exactly
 * this purpose, see its javadoc) and the {@link PasswordEncoder} bean
 * {@link PasswordEncoderConfig} already provides — no new hashing
 * dependency, per that class's javadoc ("FARELO-121 reaproveita o mesmo
 * bean").
 */
@Service
public class AuthenticationService {

    // A precomputed BCrypt hash of a string that is not any real user's
    // password, checked (and its result discarded) when email doesn't match
    // any User below. Without this, an unknown email would return
    // immediately while a known email always pays BCrypt's (deliberately
    // slow, see PasswordEncoderConfig) cost — a timing difference an
    // attacker could use to enumerate valid emails one login attempt at a
    // time, exactly what InvalidCredentialsException's javadoc says this
    // endpoint must not leak. Computed once per instance (this is a
    // singleton bean, so once per process) via the real injected encoder —
    // not a second, possibly-divergent BCryptPasswordEncoder — so its cost
    // matches whatever PasswordEncoderConfig actually configures.
    private final String dummyPasswordHash;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthenticationService(
            UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.dummyPasswordHash = passwordEncoder.encode("no-account-has-this-password-timing-safety-guard");
    }

    /**
     * Verifies {@code email}/{@code rawPassword} and, on success, issues a
     * token. Throws {@link InvalidCredentialsException} — same generic
     * failure, deliberately, in every case — when: no {@link User} has
     * {@code email}; the user exists but is {@link User#isActive()
     * inactive} (a deactivated employee, e.g. one who left, should not be
     * able to obtain a fresh token even if they still remember their
     * password); or the password doesn't match. See
     * {@link InvalidCredentialsException}'s javadoc for why these three
     * distinct causes are never distinguishable from the response.
     */
    public IssuedToken login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email).orElse(null);

        String hashToVerifyAgainst = (user != null) ? user.getPasswordHash() : dummyPasswordHash;
        boolean passwordMatches = passwordEncoder.matches(rawPassword, hashToVerifyAgainst);

        if (user == null || !user.isActive() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }

        return jwtTokenService.issue(user);
    }

}
