package com.farelo.api.table;

/**
 * Thrown when an operation references a {@link DiningTable} {@code number}
 * that does not exist (e.g. linking an unknown table to a comanda via
 * {@code CommandService#updateTable}). Same pattern as {@code
 * CommandNotFoundException}, keyed by {@code number} instead of {@code id}
 * since that's the business identifier this domain is looked up by (see
 * {@link DiningTable}'s javadoc).
 */
public class DiningTableNotFoundException extends RuntimeException {

    private final int number;

    public DiningTableNotFoundException(int number) {
        super("Dining table not found: " + number);
        this.number = number;
    }

    public int getNumber() {
        return number;
    }

}
