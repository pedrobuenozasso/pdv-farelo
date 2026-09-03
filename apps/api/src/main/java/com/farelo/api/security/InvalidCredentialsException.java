package com.farelo.api.security;

/**
 * Thrown by {@link AuthenticationService#login} when {@code email}/
 * {@code password} don't authenticate — whether because no {@link User}
 * has that email, the password is wrong, or the matching user is
 * {@link User#isActive() inactive}. See {@link AuthenticationService#login}
 * for why all three collapse to this one exception.
 *
 * <p><b>Deliberately carries nothing</b> — no email, no reason, unlike
 * {@link UserNotFoundException}/{@link UserEmailAlreadyExistsException}
 * (which carry the id/email that triggered them, fine there since those are
 * responses to an already-authenticated admin's CRUD calls). A login
 * failure is different: it's reachable by anyone who can guess an email, so
 * the message must not let a caller distinguish "this email doesn't exist"
 * from "this email exists but the password is wrong" — either would let an
 * attacker enumerate valid accounts one login attempt at a time. The
 * message is the fixed, generic string below, always.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid credentials");
    }

}
