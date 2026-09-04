package com.farelo.api.fiscal;

import com.farelo.api.command.Command;
import com.farelo.api.command.CommandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * FARELO-156 gives this class its only method so far, {@link
 * #listByCommand(int)} — read-only, backing {@code GET
 * /api/v1/commands/{number}/fiscal-documents}. Same shape as {@code
 * PaymentService}'s own first ticket (FARELO-140): no {@code create}/{@code
 * update} method exists yet, because nothing in this codebase produces a
 * real {@link FiscalDocument} yet (that is Epic 12, FARELO-170+, explicitly
 * gated on accounting validation — see {@link FiscalDocument}'s javadoc).
 *
 * <p>Depends on {@link CommandService} to resolve a business-facing comanda
 * {@code number} into a {@link Command} (404 {@code COMMAND_NOT_FOUND} via
 * the existing {@code CommandNotFoundException} when it doesn't exist) —
 * the exact same dependency direction {@code PaymentService}/{@code
 * OrderService} already have on {@code CommandService} for their own {@code
 * listByCommand(int)}.
 */
@Service
public class FiscalDocumentService {

    private final FiscalDocumentRepository fiscalDocumentRepository;
    private final CommandService commandService;

    public FiscalDocumentService(FiscalDocumentRepository fiscalDocumentRepository, CommandService commandService) {
        this.fiscalDocumentRepository = fiscalDocumentRepository;
        this.commandService = commandService;
    }

    /**
     * Lists every fiscal document recorded against a comanda, oldest first —
     * same ordering convention as {@code PaymentService#listByCommand}/
     * {@code OrderService#listByCommand}. No pagination: same YAGNI
     * reasoning already applied throughout this codebase — the number of
     * fiscal documents per comanda is naturally small (at most one per
     * eventual real emission attempt, plus any future reissue).
     *
     * @throws com.farelo.api.command.CommandNotFoundException {@code
     *     commandNumber} doesn't exist.
     */
    @Transactional(readOnly = true)
    public List<FiscalDocument> listByCommand(int commandNumber) {
        Command command = commandService.findByNumber(commandNumber);
        return fiscalDocumentRepository.findByCommandOrderByCreatedAtAsc(command);
    }

}
