package com.farelo.api.command;

/**
 * Thrown when an operation references a {@link Command} {@code number} that
 * does not exist (e.g. {@code GET /api/v1/commands/{number}} for an unknown
 * number). Same pattern as {@code CategoryNotFoundException}/
 * {@code ProductNotFoundException} in {@code com.farelo.api.catalog}, keyed
 * by {@code number} instead of {@code id} since that's the business
 * identifier this domain is looked up by (see {@link Command}'s javadoc).
 */
public class CommandNotFoundException extends RuntimeException {

    private final int number;

    public CommandNotFoundException(int number) {
        super("Command not found: " + number);
        this.number = number;
    }

    public int getNumber() {
        return number;
    }

}
