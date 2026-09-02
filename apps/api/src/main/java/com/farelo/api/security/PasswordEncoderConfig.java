package com.farelo.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Provides the {@link PasswordEncoder} used to hash {@link User} passwords
 * (FARELO-120). {@link UserService} is the only consumer today.
 *
 * <p><b>Decision — {@code spring-security-crypto}, not
 * {@code spring-boot-starter-security}, and why now rather than waiting for
 * FARELO-121</b>: this ticket needs to store a password hash, never
 * plaintext, even though the login mechanism that will eventually verify it
 * (FARELO-121) doesn't exist yet. Two dependency options were on the table:
 *
 * <ul>
 *   <li><b>Bring the full {@code spring-boot-starter-security} now</b>: the
 *   obvious "future ticket needs it anyway" argument — FARELO-121 will need
 *   it for real authentication. Rejected for <i>this</i> ticket: the moment
 *   that starter is on the classpath, Spring Boot auto-configures a default
 *   security filter chain that requires authentication on every request
 *   (httpBasic/form login, a generated password logged at startup) unless a
 *   {@code SecurityFilterChain} bean says otherwise. Every existing endpoint
 *   in this codebase (categories, products, commands, orders, print jobs,
 *   ingredients, recipes — none of them protected, by design, until
 *   FARELO-123/124) would start returning {@code 401} the instant this
 *   dependency lands, and every integration test in the suite would need a
 *   throwaway {@code permitAll()} config just to keep passing. Writing that
 *   config now would mean pre-deciding shape/scope of FARELO-121/123's real
 *   work — the opposite of what "não implemente autenticação... isso é
 *   FARELO-121/122/123/124" (this ticket's own instructions) asks for.</li>
 *   <li><b>{@code spring-security-crypto} alone (chosen)</b>: this module is
 *   deliberately standalone — {@link BCryptPasswordEncoder} and friends,
 *   with no dependency on {@code spring-security-core}/{@code -web}/
 *   {@code -config}. Spring Boot's autoconfiguration for the default
 *   security filter chain (and the generated-password login prompt) is
 *   gated on classes from those other modules, not this one, so pulling in
 *   just the crypto module gets a battle-tested hashing algorithm without
 *   any of the classpath side effects above. This also avoids the "decide
 *   the hashing library twice" cost the ticket flagged: FARELO-121 can
 *   later add the full starter for real authentication and reuse this exact
 *   {@link PasswordEncoder} bean (or replace it — Spring Security's own
 *   {@code SecurityConfig} conventions expect exactly this bean shape), no
 *   rework needed here.</li>
 * </ul>
 *
 * <p>{@link BCryptPasswordEncoder} (Spring Security's long-standing default
 * recommendation, adaptive cost factor, salted automatically) over a
 * hand-rolled hash (e.g. plain SHA-256): a general-purpose fast hash is
 * exactly the wrong tool for passwords — cheap to brute-force at scale.
 * BCrypt is intentionally slow and includes a per-hash random salt, so two
 * users with the same password never produce the same stored hash.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
