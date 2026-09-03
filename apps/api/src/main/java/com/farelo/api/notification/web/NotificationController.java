package com.farelo.api.notification.web;

import com.farelo.api.notification.Notification;
import com.farelo.api.notification.NotificationSender;
import com.farelo.api.notification.NotificationService;
import com.farelo.api.notification.NotificationStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints of the {@code notification} domain. {@link #list} is the
 * original (FARELO-110) read-only listing, mirroring the minimal first cut
 * already established for other domains (e.g. {@code Category}/FARELO-010,
 * {@code Printer}/FARELO-070). No transition endpoints ({@code /sent},
 * {@code /failed}) exist directly — {@link #send} (FARELO-111) is the one
 * write operation this domain has, and it reports the outcome ({@code
 * SENT}/{@code FAILED}) rather than accepting one, so there is nothing for
 * a client to legitimately assert onto a notification's status directly.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSender notificationSender;

    public NotificationController(NotificationService notificationService, NotificationSender notificationSender) {
        this.notificationService = notificationService;
        this.notificationSender = notificationSender;
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

    // Manual send-now endpoint (FARELO-111): not the automatic trigger the
    // prompt mestre eventually wants (ORDER_READY/estoque baixo — FARELO-
    // 112/113, still future tickets), but a way to exercise the real send
    // mechanism (NotificationSender/WhatsAppCloudApiClient) today, for
    // operability (an operator manually retrying/forcing a send) and
    // testability, per this ticket's brief. POST, not PATCH — same
    // "action, not a partial representation update" reasoning as
    // PrintJobController's /printed, /failed, /retry. No request body —
    // nothing to report beyond the notification id itself. Always 200 OK
    // whether delivery succeeded or failed: this endpoint reports an
    // *attempted* outcome (SENT or FAILED, both visible in the response
    // body's status field), not a request-validation result — a delivery
    // failure is not itself a bad request. See NotificationSender#send's
    // javadoc for why no PENDING-only guard exists before attempting.
    @PostMapping("/{id}/send")
    public NotificationResponse send(@PathVariable UUID id) {
        Notification notification = notificationSender.sendById(id);
        return NotificationResponse.from(notification);
    }

}
