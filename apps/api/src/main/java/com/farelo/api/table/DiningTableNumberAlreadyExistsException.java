package com.farelo.api.table;

/**
 * Thrown by {@code DiningTableService#create} when the requested {@code
 * number} already belongs to another {@link DiningTable}. Unlike {@link
 * com.farelo.api.command.Command}, tables are created through the API
 * itself rather than seeded by a migration (see {@link DiningTable}'s
 * javadoc), so a friendly conflict error — not just the database's raw
 * {@code UNIQUE} violation — is needed here. Same 409-shaped pattern as
 * {@code UserEmailAlreadyExistsException}/{@code RecipeAlreadyExistsException}.
 */
public class DiningTableNumberAlreadyExistsException extends RuntimeException {

    private final int number;

    public DiningTableNumberAlreadyExistsException(int number) {
        super("Dining table number already exists: " + number);
        this.number = number;
    }

    public int getNumber() {
        return number;
    }

}
