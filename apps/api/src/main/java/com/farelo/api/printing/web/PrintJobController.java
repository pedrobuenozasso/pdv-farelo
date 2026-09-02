package com.farelo.api.printing.web;

import com.farelo.api.printing.PrintJobService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * First REST endpoint of the {@code printing} domain (FARELO-076) — none
 * existed before this ticket, for {@link com.farelo.api.printing.Printer}
 * or {@link com.farelo.api.printing.PrintJob}. Exists for the Farelo Edge
 * Agent (FARELO-075, {@code apps/edge-agent}) to poll for work: which
 * {@code PrintJob}s still need to be printed.
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
    @GetMapping
    public List<PrintJobResponse> pending() {
        return printJobService.listPending().stream()
                .map(job -> PrintJobResponse.from(job, objectMapper))
                .toList();
    }

}
