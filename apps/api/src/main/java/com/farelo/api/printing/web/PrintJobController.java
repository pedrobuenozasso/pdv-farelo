package com.farelo.api.printing.web;

import com.farelo.api.printing.PrintJob;
import com.farelo.api.printing.PrintJobService;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.rbac.RequireRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * First REST endpoint of the {@code printing} domain (FARELO-076) — none
 * existed before this ticket, for {@link com.farelo.api.printing.Printer}
 * or {@link com.farelo.api.printing.PrintJob}. Exists for the Farelo Edge
 * Agent (FARELO-075, {@code apps/edge-agent}) to poll for work: which
 * {@code PrintJob}s still need to be printed.
 *
 * <h2>FARELO-124 — only {@link #retry} requires a role; {@link #pending},
 * {@link #markPrinted}, {@link #markFailed} stay unprotected</h2>
 *
 * <p>{@link #pending} (polled by the Edge Agent) and {@link #markPrinted}/
 * {@link #markFailed} (its result callbacks) are, verified against
 * {@code apps/edge-agent}'s own source ({@code src/printJobsClient.ts}),
 * called <b>exclusively</b> by the Edge Agent — a machine process running
 * on a mini PC at the café (prompt mestre seção 11), never by a person
 * with a login. {@code @RequireRole} authenticates a {@link
 * com.farelo.api.security.UserRole}-carrying {@link
 * com.farelo.api.security.User} — i.e. a person who logged in via
 * {@code POST /api/v1/auth/login} (FARELO-121). The Edge Agent has no such
 * account and was never meant to have one: prompt mestre seção 11 is
 * explicit that "o Edge Agent nunca deve possuir regra de negócio de
 * pedidos — é apenas infraestrutura de dispositivos", and giving it a
 * human-shaped login (a {@code User} row, a {@code UserRole}, credentials
 * it would have to store on the mini PC) would be exactly that kind of
 * business/identity concern leaking into what's supposed to stay
 * "just a device". A real machine-to-machine credential (e.g. a per-device
 * API key/service-account mechanism, checked by its own, distinct
 * verification path) is a legitimate future need, but it's a new
 * authentication mechanism to design, not a role to bolt onto RBAC built
 * for humans — deciding that design isn't guessed at here, same restraint
 * FARELO-120/121/122 already applied to *not* pre-deciding a future
 * ticket's mechanism (see their javadocs). So these three endpoints are
 * left exactly as unprotected as they've always been, with the reasoning
 * documented instead of silently assumed.
 *
 * <p>{@link #retry}, by contrast, is a genuinely different kind of call:
 * per its own javadoc/{@code docs/domain-model.md} (FARELO-079), it's a
 * <b>manual</b> endpoint — "não existe (ainda) nenhum retry automático
 * agendado" — and, confirmed against both {@code apps/edge-agent} and
 * {@code apps/web} source, has no current caller in either app; it exists
 * for a human to deliberately re-queue a job that failed to print. That
 * makes it a real staff action, unlike the three above, so it gets
 * {@code @RequireRole} same as any other PDV/kitchen action. <b>Role
 * choice — all five operational roles ({@code ADMIN}/{@code MANAGER}/
 * {@code CASHIER}/{@code KITCHEN}/{@code ATTENDANT})</b>: a failed print
 * job isn't owned by one station or one role — a {@code PrintJob} can be a
 * {@code BAR} or {@code KITCHEN} ticket (FARELO-074), physically printed
 * near the counter or the kitchen, and whoever is standing near the
 * silent/jammed printer when it fails (a cashier, a kitchen staffer, an
 * attendant relaying "the ticket didn't print") is exactly who should be
 * able to retry it — restricting this to a narrower subset would only
 * mean paging a manager for a low-stakes, bounded (max 3 attempts,
 * {@code PrintJobService.MAX_RETRY_COUNT}), easily-reversed action. Unlike
 * {@code UserController}'s writes (FARELO-123), nothing here can be used
 * to escalate privilege or leak data, so there's no reason to narrow it
 * further than "any authenticated staff member".
 */
@RestController
@RequestMapping("/api/v1/print-jobs")
public class PrintJobController {

    private final PrintJobService printJobService;
    private final ObjectMapper objectMapper;

    public PrintJobController(PrintJobService printJobService, ObjectMapper objectMapper) {
        this.printJobService = printJobService;
        this.objectMapper = objectMapper;
    }

    // Lists PENDING print jobs, oldest first — same shape/reasoning as
    // GET /api/v1/orders (the kitchen queue): no status query param (this
    // endpoint's entire purpose is "what's still pending", same as the
    // kitchen queue's), no pagination (YAGNI, naturally low volume), always
    // 200 OK (a list, potentially empty; no path parameter to validate).
    //
    // FARELO-124: deliberately left WITHOUT @RequireRole — Edge Agent
    // machine endpoint, see class javadoc.
    @GetMapping
    public List<PrintJobResponse> pending() {
        return printJobService.listPending().stream()
                .map(job -> PrintJobResponse.from(job, objectMapper))
                .toList();
    }

    // Reports a job successfully printed by the Edge Agent (FARELO-077):
    // PENDING -> PRINTED. POST, not PATCH — same reasoning as
    // OrderController's /deliver, /cancel: this is an action, not a partial
    // representation update. No request body — nothing to report beyond the
    // job id itself.
    //
    // FARELO-124: deliberately left WITHOUT @RequireRole — Edge Agent
    // machine endpoint, see class javadoc.
    @PostMapping("/{id}/printed")
    public PrintJobResponse markPrinted(@PathVariable UUID id) {
        PrintJob job = printJobService.markPrinted(id);
        return PrintJobResponse.from(job, objectMapper);
    }

    // Reports a job that failed to print (FARELO-077): PENDING -> FAILED.
    // Same POST-as-action reasoning as markPrinted above. No structured
    // failure reason in the body — YAGNI, see PrintJobService#markFailed's
    // javadoc.
    //
    // FARELO-124: deliberately left WITHOUT @RequireRole — Edge Agent
    // machine endpoint, see class javadoc.
    @PostMapping("/{id}/failed")
    public PrintJobResponse markFailed(@PathVariable UUID id) {
        PrintJob job = printJobService.markFailed(id);
        return PrintJobResponse.from(job, objectMapper);
    }

    // Reports a manual retry request for a FAILED job (FARELO-079):
    // FAILED -> PENDING, so it reappears in GET /api/v1/print-jobs for the
    // Edge Agent's next poll. Same POST-as-action reasoning as markPrinted/
    // markFailed above. No request body — see PrintJobService#retry's
    // javadoc for the full design rationale (manual endpoint, retry limit).
    //
    // FARELO-124: requires a staff role (all five) — human-triggered
    // action, not a machine one, see class javadoc.
    @PostMapping("/{id}/retry")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER, UserRole.KITCHEN, UserRole.ATTENDANT})
    public PrintJobResponse retry(@PathVariable UUID id) {
        PrintJob job = printJobService.retry(id);
        return PrintJobResponse.from(job, objectMapper);
    }

}
