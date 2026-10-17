package com.farelo.api.printing.web;

import com.farelo.api.printing.PrintJob;
import com.farelo.api.printing.PrintJobService;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.rbac.RequireRole;
import com.farelo.api.security.rbac.RequireDeviceKey;
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
 * <p>The queue and outcome callbacks require a dedicated printing-device key.
 * The key authenticates a machine, never grants a human role. Manual retry
 * remains a staff action protected by {@code @RequireRole}.
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
    // Edge Agent machine endpoint; requires the configured device key.
    @GetMapping
    @RequireDeviceKey
    public List<PrintJobResponse> pending() {
        return printJobService.listPending().stream()
                .map(job -> PrintJobResponse.from(job, objectMapper))
                .toList();
    }

    /** Staff-facing queue for the Admin printer panel; never accepts a device key alone. */
    @GetMapping("/staff")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER})
    public List<PrintJobResponse> pendingForStaff() {
        return pending();
    }

    // Reports a job successfully printed by the Edge Agent (FARELO-077):
    // PENDING -> PRINTED. POST, not PATCH — same reasoning as
    // OrderController's /deliver, /cancel: this is an action, not a partial
    // representation update. No request body — nothing to report beyond the
    // job id itself.
    //
    // Edge Agent machine endpoint; requires the configured device key.
    @PostMapping("/{id}/printed")
    @RequireDeviceKey
    public PrintJobResponse markPrinted(@PathVariable UUID id) {
        PrintJob job = printJobService.markPrinted(id);
        return PrintJobResponse.from(job, objectMapper);
    }

    // Reports a job that failed to print (FARELO-077): PENDING -> FAILED.
    // Same POST-as-action reasoning as markPrinted above. No structured
    // failure reason in the body — YAGNI, see PrintJobService#markFailed's
    // javadoc.
    //
    // Edge Agent machine endpoint; requires the configured device key.
    @PostMapping("/{id}/failed")
    @RequireDeviceKey
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
