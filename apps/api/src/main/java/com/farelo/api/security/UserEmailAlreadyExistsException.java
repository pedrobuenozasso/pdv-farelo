package com.farelo.api.security;

/**
 * Thrown when creating or updating a {@link User} with an email already
 * used by another user — {@code email} is required to be unique (see
 * {@link User}'s javadoc). 409 Conflict, same reasoning/shape as
 * {@code com.farelo.api.inventory.RecipeAlreadyExistsException}: the
 * request is well-formed, but conflicts with existing state.
 *
 * <p>Only the email is carried here, never a password — this exception's
 * message may end up in logs, and a password (raw or hashed) must never
 * appear there (see {@link User}'s javadoc on {@code passwordHash}).
 */
public class UserEmailAlreadyExistsException extends RuntimeException {

    private final String email;

    public UserEmailAlreadyExistsException(String email) {
        super("Email already in use: " + email);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

}
