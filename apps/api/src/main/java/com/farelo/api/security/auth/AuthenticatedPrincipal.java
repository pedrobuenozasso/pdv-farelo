package com.farelo.api.security.auth;

import com.farelo.api.security.User;
import com.farelo.api.security.UserRole;

import java.util.UUID;

/**
 * The identity carried by a validated JWT (FARELO-121) — the result of
 * {@link JwtTokenService#parse}, i.e. "who does this token claim to be,
 * assuming the signature/expiry already checked out".
 *
 * <p><b>Not used by anything yet</b>: this ticket only builds the
 * issue/verify mechanics (see {@link JwtTokenService}'s javadoc). No
 * endpoint requires a token, and nothing resolves an incoming
 * {@code Authorization} header into one of these — that's FARELO-123/124's
 * job (a request-side filter/argument resolver consuming
 * {@link JwtTokenService#parse}, protecting the Admin/PDV endpoints this
 * ticket deliberately leaves untouched). This type exists now because
 * {@code parse} needs a return shape to be a real, testable verifier rather
 * than a stub — see {@code JwtTokenServiceTests}.
 *
 * @param userId {@link User#getId()}, carried as the JWT {@code sub} claim.
 * @param email  {@link User#getEmail()} at the moment the token was issued
 *               — a snapshot, not a live lookup; a user renamed/re-emailed
 *               after login keeps their old token's claims until it expires
 *               and they log in again (no revocation mechanism yet — see
 *               {@link JwtTokenService}'s javadoc).
 * @param role   {@link User#getRole()} at the moment the token was issued,
 *               same snapshot caveat as {@code email}. Unread by anything
 *               today (RBAC is FARELO-122), but included now for the same
 *               "transcribe what the spec already decided" reasoning
 *               {@link UserRole} itself was added to {@link User} for.
 */
public record AuthenticatedPrincipal(UUID userId, String email, UserRole role) {
}
