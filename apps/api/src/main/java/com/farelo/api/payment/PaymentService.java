package com.farelo.api.payment;

import com.farelo.api.command.Command;
import com.farelo.api.command.CommandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * FARELO-140 gives this class its first (and, until FARELO-141, only)
 * method, {@link #listByCommand(int)} — read-only, backing {@code GET
 * /api/v1/commands/{number}/payments}. No {@code create}/producer method
 * exists yet: nothing in this codebase constructs a real {@link Payment}
 * until FARELO-141 ("Registrar pagamento manual") lands. Depends on {@link
 * CommandService} to resolve a business-facing comanda {@code number} into
 * a {@link Command} (404 {@code COMMAND_NOT_FOUND} via the existing {@code
 * CommandNotFoundException} when it doesn't exist) — the exact same
 * dependency direction {@code OrderService} already has on {@code
 * CommandService} for {@link com.farelo.api.ordering.OrderService#listByCommand(int)}.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final CommandService commandService;

    public PaymentService(PaymentRepository paymentRepository, CommandService commandService) {
        this.paymentRepository = paymentRepository;
        this.commandService = commandService;
    }

    /**
     * Lists every payment recorded against a comanda, oldest first — same
     * ordering convention as {@code OrderService#listByCommand}. No
     * pagination: same YAGNI reasoning already applied throughout this
     * codebase ({@code GET /api/v1/commands/{number}/orders}, {@code GET
     * /api/v1/ingredients/{ingredientId}/movements}) — the number of
     * payments per comanda is naturally small (FARELO-142 allows multiple,
     * but not an unbounded stream of them).
     */
    @Transactional(readOnly = true)
    public List<Payment> listByCommand(int commandNumber) {
        Command command = commandService.findByNumber(commandNumber);
        return paymentRepository.findByCommandOrderByCreatedAtAsc(command);
    }

}
