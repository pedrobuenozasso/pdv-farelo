package com.farelo.api.printing.web;

import com.farelo.api.printing.PrintJob;
import com.farelo.api.printing.PrintJobService;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.rbac.RequireRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes {@code POST /api/v1/commands/{number}/print-conference}
 * (FARELO-211/212) — queues a {@code COMMAND_CHECK} {@link PrintJob} (the
 * "conferência") for a comanda.
 *
 * <p><b>Placement note</b>: a command-scoped URL living in the {@code
 * printing} domain ({@code com.farelo.api.printing.web}), not in {@code
 * com.farelo.api.command.web.CommandController} — same reasoning
 * {@code com.farelo.api.ordering.web.CommandOrdersController}'s javadoc
 * already documents for {@code GET /api/v1/commands/{number}/orders}:
 * {@code printing} already depends on {@code command} ({@link
 * PrintJobService} now calls {@code CommandService#findByNumber}, see its
 * javadoc), so keeping this controller here too keeps that dependency
 * one-directional instead of making {@code command.web} depend back on
 * {@code printing.PrintJobService}.
 *
 * <p><b>Role list</b>: same as {@code CommandController}'s {@code open}/
 * {@code close} and {@code OrderController}'s {@code markAsDelivered}/
 * {@code markAsCancelled} — requesting a conferência is a front-of-house
 * action (the PDV screen, deciding when to hand the customer a bill
 * preview), not a kitchen one, so {@code KITCHEN} is excluded, matching
 * every other staff-facing comanda action in this codebase.
 */
@RestController
@RequestMapping("/api/v1/commands")
public class CommandPrintConferenceController {

    private final PrintJobService printJobService;
    private final ObjectMapper objectMapper;

    public CommandPrintConferenceController(PrintJobService printJobService, ObjectMapper objectMapper) {
        this.printJobService = printJobService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{number}/print-conference")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER, UserRole.ATTENDANT})
    public PrintJobResponse printConference(@PathVariable int number) {
        PrintJob job = printJobService.createCommandCheck(number);
        return PrintJobResponse.from(job, objectMapper);
    }

}
