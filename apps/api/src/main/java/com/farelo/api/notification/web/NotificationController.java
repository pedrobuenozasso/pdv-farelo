package com.farelo.api.notification.web;

import com.farelo.api.notification.NotificationService;
import com.farelo.api.notification.NotificationStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * First (and, for this ticket, only) REST endpoint of the {@code
 * notification} domain (FARELO-110) — a read-only listing, mirroring the
 * minimal first cut already established for other domains (e.g. {@code
 * Category}/FARELO-010, {@code Printer}/FARELO-070). No creation endpoint:
 * nothing in this ticket constructs a real {@code Notification} (that's a
 * future automatic trigger, FARELO-112/113 — see {@code Notification}'s
 * javadoc), so there is nothing for a human/client to legitimately POST
 * here yet. No transition endpoints ({@code /sent}, {@code /failed})
 * either, for the same reason {@code com.farelo.api.printing.PrintJob}'s
 * equivalents didn't exist at its own entity-only ticket (FARELO-071) — no
 * real caller exists yet to validate a transition rule against.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // Optional ?status= filter (e.g. GET /api/v1/notifications?status=PENDING):
    // unlike GET /api/v1/print-jobs (which only ever needs "what's still
    // pending" for its one real consumer, the Edge Agent), this endpoint has
    // no real consumer yet at all — it exists for general visibility (e.g. a
    // future Admin screen, or manual inspection) as much as for a future
    // worker's polling, so filtering to a single status is opt-in rather
    // than the endpoint's only mode. Always 200 OK (a list, potentially
    // empty; no path parameter to validate) — same as GET /api/v1/print-jobs.
    @GetMapping
    public List<NotificationResponse> list(@RequestParam(required = false) NotificationStatus status) {
        return notificationService.list(status).stream()
                .map(NotificationResponse::from)
                .toList();
    }

}
