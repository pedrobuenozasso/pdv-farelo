package com.farelo.api.fiscal.web;

import com.farelo.api.fiscal.FiscalDocument;
import com.farelo.api.fiscal.FiscalDocumentService;
import com.farelo.api.fiscal.FiscalPendingCommandService;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.rbac.RequireRole;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Exposes {@code GET /api/v1/commands/{number}/fiscal-documents}
 * (FARELO-156) and {@code POST
 * /api/v1/commands/{number}/fiscal-documents/{id}/transition}
 * (FARELO-157). Same "minimal read-only listing first" precedent already
 * followed by {@code InventoryMovement}/{@code Notification}/{@code
 * AuditLog}/{@code Payment} at their own first tickets. The explicit
 * {@code POST .../pending} now creates or reuses an untouched placeholder
 * for a closed paid sale; it does not prepare or emit NFC-e, and there is
 * no free-form create/edit surface.
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
 * <p>Reading fiscal documents requires {@code ADMIN} or {@code MANAGER},
 * the same roles allowed to perform the manual transition below.
 *
 * <h2>{@link #transition} — endpoint-exposure decision (FARELO-157)</h2>
 *
 * {@code FiscalDocumentService#transition(UUID, FiscalDocumentStatus)} (the
 * validated state-machine move — see its javadoc for the full transition
 * table) has no automatic caller yet: no real emission producer exists
 * until Epic 12 (FARELO-170+, explicitly gated). Two options were
 * considered for this ticket, same choice {@code PrintJobService#retry}
 * (FARELO-079) already framed explicitly in its own javadoc: (a) a manual
 * endpoint so the validated transition is reachable/testable/operable today
 * even with no automatic driver, or (b) leave it purely as backend
 * infrastructure with no endpoint yet — the {@code
 * InventoryMovementRepository#sumQuantityByIngredientId}-style "query
 * exists before its first real caller" shape.
 *
 * <p>This ticket takes (a), a manual {@code POST
 * /api/v1/commands/{number}/fiscal-documents/{id}/transition}, for the same
 * core reason {@code PrintJobController#retry} did: a validated transition
 * with zero real callers and zero HTTP coverage is much harder to trust
 * once Epic 12 actually starts calling it — an integration test hitting a
 * real endpoint exercises the exact same code path
 * {@code FiscalDocumentService#transition(int, UUID, FiscalDocumentStatus)}
 * a future producer will also go through (comanda resolution, ownership
 * check, the transition table, persistence), rather than only ever being
 * called directly from a unit test. Building the manual path now doesn't
 * paint Epic 12 into a corner either: nothing prevents a future emission
 * process from calling the same underlying {@code
 * FiscalDocumentService#transition(UUID, FiscalDocumentStatus)} method
 * directly (bypassing this HTTP layer entirely, as it naturally would from
 * inside a backend process), leaving this endpoint as a genuinely separate,
 * still-useful manual/operability surface (e.g. support staff manually
 * resolving a stuck document) rather than something to delete later. As
 * Epic 12 records real SEFAZ outcomes, this route only permits preparatory
 * moves; it cannot assert authorization, rejection or cancellation.
 *
 * <p><b>{@link #transition} requires {@code ADMIN} or {@code MANAGER}</b>,
 * unlike {@code PrintJobController#retry}'s deliberately broad
 * "all five roles" choice: a print retry is low-stakes and easily reversed
 * (re-print a ticket, capped at 3 attempts), station-agnostic, and
 * something anyone standing near a jammed printer should be able to do.
 * Manually flipping a fiscal document's status is not that — even without
 * Epic 12's real SEFAZ integration yet, this action writes to a record this
 * codebase already treats as fiscally/legally significant (seção 26/27:
 * "configuração fiscal" is explicitly named among the operations audit
 * logging must cover), so it is scoped the same as {@code
 * PaymentController}'s write endpoints ({@code record}/{@code close}:
 * {@code ADMIN}/{@code MANAGER}/{@code CASHIER}) minus {@code CASHIER} —
 * recording a payment or closing a comanda is routine day-to-day PDV work
 * a cashier does constantly, while manually forcing a fiscal document's
 * state is not a normal operational action for any role yet (nothing in
 * this codebase's UI/flow calls it) and is closer to a back-office/
 * compliance correction, so it stays with the two roles that already cover
 * that kind of responsibility elsewhere in this codebase.
 */
@RestController
@RequestMapping("/api/v1/commands")
public class FiscalDocumentController {

    private final FiscalDocumentService fiscalDocumentService;
    private final FiscalPendingCommandService pendingCommandService;

    public FiscalDocumentController(FiscalDocumentService fiscalDocumentService,
                                    FiscalPendingCommandService pendingCommandService) {
        this.fiscalDocumentService = fiscalDocumentService;
        this.pendingCommandService = pendingCommandService;
    }

    // {number} is the command's business identifier, same convention as
    // com.farelo.api.payment.web.PaymentController /
    // com.farelo.api.ordering.web.CommandOrdersController.
    @GetMapping("/{number}/fiscal-documents")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER})
    public List<FiscalDocumentResponse> listByCommand(@PathVariable int number) {
        return fiscalDocumentService.listByCommand(number).stream()
                .map(FiscalDocumentResponse::from)
                .toList();
    }

    @PostMapping("/{number}/fiscal-documents/pending")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER})
    public FiscalPendingDocumentResponse startPending(@PathVariable int number) {
        return FiscalPendingDocumentResponse.from(pendingCommandService.start(number));
    }

    // FARELO-157: manual transition endpoint — see class javadoc,
    // "transition — endpoint-exposure decision", for why this exists and
    // why it's role-protected unlike listByCommand above. POST, not PATCH
    // — same "action, not partial representation update" reasoning as
    // OrderController's /deliver, /cancel and PrintJobController's
    // /printed, /failed, /retry.
    @PostMapping("/{number}/fiscal-documents/{id}/transition")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER})
    public FiscalDocumentResponse transition(
            @PathVariable int number,
            @PathVariable UUID id,
            @Valid @RequestBody FiscalDocumentTransitionRequest request) {
        FiscalDocument document = fiscalDocumentService.transition(number, id, request.status());
        return FiscalDocumentResponse.from(document);
    }

}
