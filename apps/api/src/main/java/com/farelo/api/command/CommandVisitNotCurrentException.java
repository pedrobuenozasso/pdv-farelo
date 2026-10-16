package com.farelo.api.command;

/** A historical or closed visit cannot receive new operational changes. */
public class CommandVisitNotCurrentException extends RuntimeException {

    public CommandVisitNotCurrentException(int number, int cycleNumber) {
        super("Command %d visit %d is closed or no longer current".formatted(number, cycleNumber));
    }
}
