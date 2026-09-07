package com.farelo.api.cashier;

import java.util.UUID;

/**
 * Thrown when an operation references a {@link Shift} id that does not
 * exist — e.g. {@code CashSessionService#open} (FARELO-307) resolving the
 * {@code shiftId} given at cash-session-open time.
 */
public class ShiftNotFoundException extends RuntimeException {

    private final UUID shiftId;

    public ShiftNotFoundException(UUID shiftId) {
        super("Shift not found: " + shiftId);
        this.shiftId = shiftId;
    }

    public UUID getShiftId() {
        return shiftId;
    }

}
