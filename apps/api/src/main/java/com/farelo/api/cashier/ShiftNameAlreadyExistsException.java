package com.farelo.api.cashier;

/**
 * Thrown by {@link ShiftService#create} when {@code name} is already taken
 * by another {@link Shift} — see {@link Shift}'s javadoc for why the name
 * is unique ({@code uk_shift_name}).
 */
public class ShiftNameAlreadyExistsException extends RuntimeException {

    private final String name;

    public ShiftNameAlreadyExistsException(String name) {
        super("Shift name already exists: " + name);
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
