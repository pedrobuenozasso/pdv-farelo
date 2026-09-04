package com.farelo.api.fiscal;

import com.farelo.api.command.Command;
import com.farelo.api.command.CommandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Collections;

/**
 * FARELO-156 gave this class its first method, {@link #listByCommand(int)} —
 * read-only, backing {@code GET /api/v1/commands/{number}/fiscal-documents}.
 * FARELO-157 ("Criar estados fiscais") adds this class's second and third
 * methods, {@link #transition(UUID, FiscalDocumentStatus)}/{@link
 * #transition(int, UUID, FiscalDocumentStatus)} — the state-machine
 * validation {@link FiscalDocumentStatus}'s own javadoc named as a
 * separate, later ticket, plus (see "Endpoint exposure" below) the manual
 * HTTP surface to drive it. Still no {@code create} method: nothing in this
 * codebase produces a real {@link FiscalDocument} yet (that is Epic 12,
 * FARELO-170+, explicitly gated on accounting validation — see {@link
 * FiscalDocument}'s javadoc), and this ticket does not start Epic 12 either
 * — see "Critical scoping boundary" below.
 *
 * <p>Depends on {@link CommandService} to resolve a business-facing comanda
 * {@code number} into a {@link Command} (404 {@code COMMAND_NOT_FOUND} via
 * the existing {@code CommandNotFoundException} when it doesn't exist) —
 * the exact same dependency direction {@code PaymentService}/{@code
 * OrderService} already have on {@code CommandService} for their own {@code
 * listByCommand(int)}.
 *
 * <h2>Critical scoping boundary (FARELO-157)</h2>
 *
 * This class does not gain any real emission logic here. {@link
 * #transition(UUID, FiscalDocumentStatus)} only validates and applies a
 * <em>requested</em> status move — it never decides, on its own, that a
 * document should become {@code AUTHORIZED} or {@code REJECTED}; that
 * decision (calling SEFAZ, parsing a real response) remains Epic 12's job
 * (FARELO-170-178), still gated and still not started. Nothing in this
 * codebase calls {@link #transition(UUID, FiscalDocumentStatus)}
 * automatically today — same "rules exist, no automatic driver yet" shape
 * already established by {@code CommandService}'s own {@code
 * CLOSABLE_STATUSES}/{@code PAYABLE_STATUSES} (validated membership sets
 * with no business logic deciding *when* to call them), generalized here
 * from a flat set into a full origin→destination transition table since
 * {@link FiscalDocumentStatus} genuinely has more than one legal
 * destination per origin (unlike, say, {@code PrintJobService}'s
 * single-origin {@code markPrinted}/{@code markFailed}).
 *
 * <h2>The transition table (FARELO-157)</h2>
 *
 * {@link #LEGAL_TRANSITIONS} is the full, closed table — every edge not
 * listed is illegal. Grounded directly in the prompt mestre (seção 25:
 * {@code Close Command → Payment → Fiscal Service → NFC-e → SEFAZ →
 * AUTHORIZED}) plus standard, well-established NFC-e/SEFAZ domain
 * knowledge (not invented), and corroborated by the literal ordering of
 * Epic 12's own tickets (seção 47): FARELO-175 "Persistir autorização",
 * FARELO-176 "Persistir rejeição", FARELO-177 "Implementar cancelamento",
 * FARELO-178 "Implementar contingência" — i.e. the roadmap itself already
 * treats "authorize", "reject", "cancel" and "enter contingency" as four
 * distinct events, not stages of one linear chain.
 *
 * <ul>
 *   <li><b>{@code PENDING} → {@code PROCESSING}</b>: the happy-path start —
 *       a document begins being transmitted to SEFAZ.</li>
 *   <li><b>{@code PENDING} → {@code CONTINGENCY}</b>: SEFAZ can be
 *       unreachable before an emission attempt is even sent (e.g. the
 *       internet link at the café is down at the moment "fechar a conta"
 *       happens) — contingency mode can begin without ever reaching {@code
 *       PROCESSING}, so this edge is legal.</li>
 *   <li><b>{@code PENDING} → {@code AUTHORIZED}/{@code REJECTED}/{@code
 *       CANCELLED}</b>: all illegal. {@code AUTHORIZED}/{@code REJECTED}
 *       both mean SEFAZ evaluated the document — that requires having sent
 *       it first ({@code PROCESSING}), so skipping straight from {@code
 *       PENDING} contradicts the seção 25 order. {@code CANCELLED} is a
 *       real NFC-e legal event ("cancelamento") that only applies to a
 *       document SEFAZ already authorized — see the {@code AUTHORIZED} →
 *       {@code CANCELLED} edge below; a never-sent {@code PENDING} document
 *       has nothing to cancel in that sense, so this edge is conservatively
 *       excluded rather than guessed at.</li>
 *   <li><b>{@code PROCESSING} → {@code AUTHORIZED}</b>: the happy path's
 *       final step — SEFAZ accepted the document (FARELO-175, "Persistir
 *       autorização").</li>
 *   <li><b>{@code PROCESSING} → {@code REJECTED}</b>: SEFAZ evaluated the
 *       document and rejected it — a legitimate, expected outcome of
 *       transmission, distinct from never having been sent at all
 *       (FARELO-176, "Persistir rejeição").</li>
 *   <li><b>{@code PROCESSING} → {@code CONTINGENCY}</b>: SEFAZ can also
 *       become unreachable mid-transmission (timeout, connection dropped
 *       while awaiting a response) — same contingency detour as from
 *       {@code PENDING}, just triggered at a later point in the attempt.</li>
 *   <li><b>{@code PROCESSING} → {@code PENDING}/{@code CANCELLED}</b>: both
 *       illegal. Reverting {@code PROCESSING} back to {@code PENDING} (e.g.
 *       to "reset" a stuck in-flight attempt) is a plausible future need,
 *       but nothing in the prompt mestre or Epic 12's ticket list names it,
 *       and this table is the guard-rail a not-yet-built Epic 12 will rely
 *       on — the conservative default is to leave an edge out until a real
 *       need names it, not to guess one in now (an unneeded rule can only
 *       be too strict and caught by a future ticket's own tests; a wrongly
 *       *permissive* rule could let bad data through silently before
 *       anything exercises it for real). {@code CANCELLED} is excluded for
 *       the same "not yet authorized, nothing to cancel" reasoning as the
 *       {@code PENDING} → {@code CANCELLED} edge above.</li>
 *   <li><b>{@code CONTINGENCY} → {@code AUTHORIZED}/{@code REJECTED}</b>:
 *       per the prompt mestre's own framing (a contingency detour
 *       "eventually still needs to resolve toward {@code AUTHORIZED}/{@code
 *       REJECTED} once connectivity returns"), a document regularized after
 *       being issued in contingency is ultimately still either accepted or
 *       rejected by SEFAZ — same two legitimate outcomes as {@code
 *       PROCESSING}, just reached via the contingency detour instead of a
 *       direct transmission.</li>
 *   <li><b>{@code CONTINGENCY} → {@code PROCESSING}/{@code PENDING}/{@code
 *       CANCELLED}</b>: all illegal, same conservative-default reasoning as
 *       {@code PROCESSING} → {@code PENDING} above — a "resume the
 *       in-flight attempt" edge back through {@code PROCESSING} is
 *       plausible once connectivity returns, but is not named anywhere in
 *       the source material, so it is left out rather than guessed at;
 *       {@link #transition(UUID, FiscalDocumentStatus)} lets a future
 *       ticket resolve {@code CONTINGENCY} directly to {@code AUTHORIZED}/
 *       {@code REJECTED} without needing this edge at all. {@code
 *       CANCELLED} again requires prior {@code AUTHORIZED} status.</li>
 *   <li><b>{@code AUTHORIZED} → {@code CANCELLED}</b>: the one legitimate
 *       backward-looking move in this table — cancelling an
 *       already-authorized NFC-e ("cancelamento", FARELO-177, "Implementar
 *       cancelamento") is a real, distinct SEFAZ operation for an
 *       already-issued document, categorically different from {@code
 *       REJECTED} (which means SEFAZ never accepted the document in the
 *       first place).</li>
 *   <li><b>{@code AUTHORIZED} → anything else</b>: illegal. An authorized
 *       NFC-e cannot retroactively become {@code REJECTED} (SEFAZ already
 *       accepted it — undoing that is exactly what {@code CANCELLED}
 *       exists for, as a distinct legal event, not a reuse of rejection),
 *       nor back to {@code PENDING}/{@code PROCESSING}/{@code CONTINGENCY}
 *       (there is nothing left to (re)transmit).</li>
 *   <li><b>{@code REJECTED} — terminal, no outgoing edges</b>: standard
 *       NFC-e practice is to fix whatever caused the rejection and issue a
 *       <em>new</em> fiscal document (a new {@code FiscalDocument} row),
 *       not to mutate the rejected one back to {@code PENDING} and retry
 *       with the same row/number — the rejected document's own record
 *       should stay exactly what it was when SEFAZ rejected it, so no edge
 *       leaves {@code REJECTED}. This is also the conservative default
 *       applied consistently across this whole table.</li>
 *   <li><b>{@code CANCELLED} — terminal, no outgoing edges</b>: cancellation
 *       is itself a final legal event; there is no legitimate "uncancel".</li>
 * </ul>
 *
 * <p>No self-transitions (e.g. {@code PENDING} → {@code PENDING}) are
 * legal either — every pair not explicitly listed above is rejected the
 * same way, same "no idempotent same-state special case" behavior already
 * established by {@code OrderService#transition}/{@code
 * PrintJobService}'s transition helper.
 */
@Service
public class FiscalDocumentService {

    // FARELO-157: the full, closed transition table — see this class's
    // javadoc, "The transition table", for the reasoning behind every edge.
    // A Map<FiscalDocumentStatus, Set<FiscalDocumentStatus>> (an
    // EnumMap<..., EnumSet<...>>, same "closed enum → EnumSet" convention
    // CommandService's CLOSABLE_STATUSES/PAYABLE_STATUSES already use) is a
    // generalized version of that same "validated set, no logic deciding
    // when to call it" shape: those two constants only ever needed a flat
    // set of valid origins for one fixed destination, but
    // FiscalDocumentStatus genuinely has more than one legal destination per
    // origin, so a per-origin set of legal destinations is the shape this
    // table actually needs. Built once, immutable, and never mutated after
    // construction.
    private static final Map<FiscalDocumentStatus, Set<FiscalDocumentStatus>> LEGAL_TRANSITIONS =
            buildLegalTransitions();

    private static Map<FiscalDocumentStatus, Set<FiscalDocumentStatus>> buildLegalTransitions() {
        Map<FiscalDocumentStatus, Set<FiscalDocumentStatus>> transitions =
                new EnumMap<>(FiscalDocumentStatus.class);
        transitions.put(
                FiscalDocumentStatus.PENDING,
                EnumSet.of(FiscalDocumentStatus.PROCESSING, FiscalDocumentStatus.CONTINGENCY));
        transitions.put(
                FiscalDocumentStatus.PROCESSING,
                EnumSet.of(
                        FiscalDocumentStatus.AUTHORIZED,
                        FiscalDocumentStatus.REJECTED,
                        FiscalDocumentStatus.CONTINGENCY));
        transitions.put(
                FiscalDocumentStatus.CONTINGENCY,
                EnumSet.of(FiscalDocumentStatus.AUTHORIZED, FiscalDocumentStatus.REJECTED));
        transitions.put(FiscalDocumentStatus.AUTHORIZED, EnumSet.of(FiscalDocumentStatus.CANCELLED));
        transitions.put(FiscalDocumentStatus.REJECTED, EnumSet.noneOf(FiscalDocumentStatus.class));
        transitions.put(FiscalDocumentStatus.CANCELLED, EnumSet.noneOf(FiscalDocumentStatus.class));
        return Collections.unmodifiableMap(transitions);
    }

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

    /**
     * Fetches a single {@link FiscalDocument} by its own id, with {@code
     * command} eagerly loaded (see {@link
     * FiscalDocumentRepository#findByIdWithCommand(UUID)}).
     *
     * @throws FiscalDocumentNotFoundException if no document exists for
     *     {@code id}.
     */
    @Transactional(readOnly = true)
    public FiscalDocument getById(UUID id) {
        return fiscalDocumentRepository.findByIdWithCommand(id)
                .orElseThrow(() -> new FiscalDocumentNotFoundException(id));
    }

    /**
     * FARELO-157's core method: validates that moving the {@link
     * FiscalDocument} identified by {@code id} to {@code newStatus} is
     * legal per {@link #LEGAL_TRANSITIONS} (see this class's javadoc for
     * the full table and reasoning), applies it via {@link
     * FiscalDocument#setStatus(FiscalDocumentStatus)} and saves.
     *
     * <p>Deliberately takes only {@code id} and {@code newStatus} — no
     * comanda {@code number} — matching the shape a future Epic 12
     * producer (FARELO-170+) is expected to call this with: an emission
     * process driving a specific {@code FiscalDocument} it already holds
     * (e.g. just received a SEFAZ response for it) has no natural reason to
     * also know or re-resolve that document's comanda {@code number}. See
     * the overload below for the comanda-scoped variant the manual HTTP
     * endpoint actually calls.
     *
     * @throws FiscalDocumentNotFoundException if no document exists for
     *     {@code id}.
     * @throws FiscalDocumentInvalidTransitionException if the document's
     *     current status has no legal edge to {@code newStatus} in {@link
     *     #LEGAL_TRANSITIONS}.
     */
    @Transactional
    public FiscalDocument transition(UUID id, FiscalDocumentStatus newStatus) {
        FiscalDocument document = getById(id);
        return applyTransition(document, newStatus);
    }

    /**
     * Comanda-scoped overload backing {@code POST
     * /api/v1/commands/{number}/fiscal-documents/{id}/transition} (see
     * {@code FiscalDocumentController} for the endpoint-exposure decision).
     * Resolves {@code commandNumber} the same way {@link
     * #listByCommand(int)} does, then additionally confirms the document
     * identified by {@code id} actually belongs to that comanda — same
     * "nested resource must belong to its named parent, or it's treated as
     * 404" precedent already established by {@code
     * RecipeItemService#delete(UUID, UUID)} (via {@code
     * RecipeItemRepository#findByIdAndRecipeId}) for {@code DELETE
     * /api/v1/recipes/{recipeId}/items/{itemId}} — a mismatched {@code
     * number}/{@code id} pair (e.g. a valid document id borrowed from a
     * different comanda's URL) is reported as {@link
     * FiscalDocumentNotFoundException}, not silently allowed or exposed as
     * a distinct "wrong command" error that would leak whether the id
     * exists at all under some other comanda.
     *
     * <p>Delegates the actual legality check/mutation to {@link
     * #transition(UUID, FiscalDocumentStatus)} above once ownership is
     * confirmed — this overload only adds the comanda-scoping concern, not
     * a second copy of the transition rules.
     *
     * @throws com.farelo.api.command.CommandNotFoundException if {@code
     *     commandNumber} doesn't exist.
     * @throws FiscalDocumentNotFoundException if no document exists for
     *     {@code id}, or it exists but belongs to a different comanda than
     *     {@code commandNumber}.
     * @throws FiscalDocumentInvalidTransitionException same as {@link
     *     #transition(UUID, FiscalDocumentStatus)}.
     */
    @Transactional
    public FiscalDocument transition(int commandNumber, UUID id, FiscalDocumentStatus newStatus) {
        Command command = commandService.findByNumber(commandNumber);
        FiscalDocument document = getById(id);

        if (!document.getCommand().getId().equals(command.getId())) {
            throw new FiscalDocumentNotFoundException(id);
        }

        return applyTransition(document, newStatus);
    }

    // Shared by both transition(...) overloads above: validate the
    // document's current status has a legal edge to newStatus in
    // LEGAL_TRANSITIONS, mutate, save. Same read-check-write shape as
    // OrderService#transition/PrintJobService's private transition helper.
    private FiscalDocument applyTransition(FiscalDocument document, FiscalDocumentStatus newStatus) {
        FiscalDocumentStatus currentStatus = document.getStatus();
        Set<FiscalDocumentStatus> allowedDestinations =
                LEGAL_TRANSITIONS.getOrDefault(currentStatus, EnumSet.noneOf(FiscalDocumentStatus.class));

        if (!allowedDestinations.contains(newStatus)) {
            throw new FiscalDocumentInvalidTransitionException(document.getId(), currentStatus, newStatus);
        }

        document.setStatus(newStatus);
        return fiscalDocumentRepository.save(document);
    }

}
