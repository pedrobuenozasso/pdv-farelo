package com.farelo.api.fiscal.web;

import com.farelo.api.fiscal.FiscalDocumentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Exposes {@code GET /api/v1/commands/{number}/fiscal-documents}
 * (FARELO-156) — the only endpoint this ticket adds. Same "minimal
 * read-only listing first" precedent already followed by {@code
 * InventoryMovement}/{@code Notification}/{@code AuditLog}/{@code Payment}
 * at their own first tickets: {@link com.farelo.api.fiscal.FiscalDocument}
 * is a system-generated fact (once something eventually produces one — Epic
 * 12), not something an
 * Admin configures, so there is no {@code POST}/{@code PUT} surface here
 * (contrast {@code FiscalProfile}/{@code CompanyFiscalConfiguration}, both
 * Admin-configured lookup values).
 *
 * <p><b>Placement note</b>: this is a command-scoped URL, but lives in the
 * {@code fiscal} domain ({@code com.farelo.api.fiscal.web}), not in {@code
 * com.farelo.api.command.web.CommandController}. Same dependency-direction
 * reasoning {@code PaymentController}'s javadoc already documents for the
 * analogous choice in {@code payment}: {@code fiscal} already depends on
 * {@code command} ({@link com.farelo.api.fiscal.FiscalDocument#getCommand()}
 * is a required {@code @ManyToOne}, and {@link FiscalDocumentService} calls
 * {@code CommandService} to resolve a business-facing comanda {@code
 * number}), so keeping this controller here too keeps that dependency
 * one-directional. Putting it in {@code CommandController} instead would
 * make {@code command.web} depend on {@code fiscal.FiscalDocumentService}, a
 * cross-domain dependency in the opposite direction (AGENTS.md: "evitar
 * dependências cruzadas desnecessárias").
 *
 * <p><b>Stays unprotected</b>: same "leave a domain's first read endpoint
 * unprotected" precedent already followed by {@code Notification}
 * (FARELO-110), {@code AuditLog} (FARELO-125) and {@code Payment}'s own
 * {@code listByCommand} (FARELO-140) at their own first tickets — no ticket
 * dedicated to RBAC enforcement for {@code fiscal} exists yet.
 */
@RestController
@RequestMapping("/api/v1/commands")
public class FiscalDocumentController {

    private final FiscalDocumentService fiscalDocumentService;

    public FiscalDocumentController(FiscalDocumentService fiscalDocumentService) {
        this.fiscalDocumentService = fiscalDocumentService;
    }

    // {number} is the command's business identifier, same convention as
    // com.farelo.api.payment.web.PaymentController /
    // com.farelo.api.ordering.web.CommandOrdersController.
    @GetMapping("/{number}/fiscal-documents")
    public List<FiscalDocumentResponse> listByCommand(@PathVariable int number) {
        return fiscalDocumentService.listByCommand(number).stream()
                .map(FiscalDocumentResponse::from)
                .toList();
    }

}
