package com.farelo.api.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Polls {@code notification} for {@code PENDING} rows and sends them
 * (FARELO-112) — the {@code Notification Worker} named literally in the
 * prompt mestre (seção 19): "Fluxo: {@code ORDER_READY → Notification
 * Worker → WhatsApp}". First real caller of {@link
 * NotificationService#listPending()} (see that method's javadoc) and of
 * {@link NotificationSender#send(Notification)} outside the manual {@code
 * POST /api/v1/notifications/{id}/send} endpoint.
 *
 * <h2>Why a separate worker/schedule from {@code OutboxWorker}</h2>
 *
 * {@code com.farelo.api.outbox.OutboxWorker} is where a {@code PENDING}
 * {@link Notification} row gets <em>created</em> for an {@code OrderReady}
 * event (via {@link OrderReadyNotificationService}) — but creating that row
 * is a plain database write, the same shape as {@code
 * com.farelo.api.printing.PrintJobService#createForOrder}. Actually
 * <em>sending</em> a notification is a different kind of operation
 * entirely: {@link NotificationSender#send(Notification)} makes a real
 * outbound HTTP call to the Meta WhatsApp Cloud API (or, in dev/test, a
 * local stub) — exactly the kind of slow/unreliable external call {@code
 * OutboxWorker}'s own design keeps out of its {@code @Transactional} batch
 * (see its javadoc: dispatch failures roll back the whole batch, and every
 * event in a batch is drained under held {@code FOR UPDATE} row locks).
 * Calling {@code NotificationSender.send(...)} straight from {@code
 * OutboxWorker.dispatch(...)} would put that same outbound call back inside
 * a transaction holding those locks — the opposite of what that worker's
 * design is protecting against, just moved onto a different table.
 *
 * <p>This class exists to keep those two concerns on two independent
 * schedules and transaction boundaries: {@code OutboxWorker} only ever does
 * fast, local database writes; this class is where the slow, external I/O
 * happens, entirely decoupled from outbox's own batch/locking machinery.
 *
 * <h2>No {@code @Transactional} on {@link #processPendingNotifications()}
 * itself</h2>
 *
 * Deliberately so. {@link NotificationSender#send(Notification)} is already
 * {@code @Transactional} <em>per call</em> — if this method were annotated
 * too, Spring's default propagation would fold every {@code send} call for
 * the whole batch into one enclosing transaction, which is exactly the
 * "slow HTTP call inside a held transaction" shape this class exists to
 * avoid, just relocated one level up. Leaving this method unannotated means
 * each {@link Notification} in a batch is attempted, and its outcome
 * persisted, independently — one slow or failing recipient never holds a
 * lock or a transaction open for its neighbors, and (since {@link
 * NotificationSender#send(Notification)} already never lets a delivery
 * failure throw — see its own javadoc) one bad send never aborts the rest
 * of the batch either.
 *
 * <h2>Known limitation — no {@code SKIP LOCKED} (unlike {@code
 * OutboxWorker})</h2>
 *
 * {@link #processPendingNotifications()} lists every currently {@code
 * PENDING} notification via a plain query, with no row-level locking. That
 * is safe today because — same as {@code OutboxWorker} before FARELO-063
 * added {@code SKIP LOCKED} (see its javadoc, "Safe under concurrent worker
 * instances") — only one instance of this application runs, and a {@code
 * @Scheduled} method with the default (non-overlapping) {@code fixedDelay}
 * never races a copy of itself within one JVM. If this application ever
 * scales to multiple concurrent instances, this method would need the same
 * treatment {@code OutboxEventRepository#findPendingForUpdateSkipLocked}
 * already gives outbox events — deliberately not built preemptively here,
 * same YAGNI discipline the outbox package applied until a concrete need
 * (horizontal scaling) actually existed.
 *
 * <p>No batch-size cap either (unlike {@code OutboxWorker}'s {@code
 * outbox.worker.batch-size}) — same "naturally low volume" reasoning already
 * applied to {@code
 * com.farelo.api.printing.PrintJobService#listPending()}/{@code
 * OrderService#listQueue()}: at most one {@code ORDER_READY} notification
 * per order reaching {@code READY}, which is not a high-volume event.
 *
 * <p><strong>Poll interval</strong>: {@code
 * notification.worker.poll-interval-ms}, default 5000 (see {@code
 * application.yml}) — same configurable-rather-than-literal reasoning as
 * {@code outbox.worker.poll-interval-ms} (see {@code OutboxWorker}'s
 * javadoc): lets a test push it far out and call {@link
 * #processPendingNotifications()} directly instead of depending on
 * wall-clock timing. See {@code AbstractIntegrationTest}, which disables
 * this trigger suite-wide for the exact same reason it already disables
 * {@code OutboxWorker}'s.
 *
 * <p><strong>{@code initialDelayString}, same property</strong> — carries
 * {@code OutboxWorker}'s own fix for the identical bug (found in review
 * while verifying this ticket, not part of its original scope): {@code
 * @Scheduled(fixedDelayString = ...)} alone fires its first execution
 * immediately on scheduler startup, before the configured interval — and
 * therefore before {@code AbstractIntegrationTest}'s 3600000ms
 * "disabled" override — has any effect. See {@code OutboxWorker}'s javadoc
 * for the full reasoning and the concrete failure this caused.
 */
@Component
public class NotificationWorker {

    private static final Logger log = LoggerFactory.getLogger(NotificationWorker.class);

    private final NotificationService notificationService;
    private final NotificationSender notificationSender;

    public NotificationWorker(NotificationService notificationService, NotificationSender notificationSender) {
        this.notificationService = notificationService;
        this.notificationSender = notificationSender;
    }

    /**
     * Sends every currently {@code PENDING} notification, oldest first
     * (FIFO), each in its own independent attempt (see class javadoc for
     * why this method itself is not {@code @Transactional}). Returns the
     * batch it just attempted — mainly so tests can assert on exactly what
     * one call touched; production code (the {@code @Scheduled} trigger)
     * ignores the return value.
     */
    @Scheduled(
            fixedDelayString = "${notification.worker.poll-interval-ms:5000}",
            initialDelayString = "${notification.worker.poll-interval-ms:5000}")
    public List<Notification> processPendingNotifications() {
        List<Notification> pending = notificationService.listPending();

        for (Notification notification : pending) {
            // NotificationSender#send already never lets a delivery
            // failure throw (it catches WhatsAppSendException internally
            // and marks the notification FAILED) — nothing to catch here.
            notificationSender.send(notification);
        }

        if (!pending.isEmpty()) {
            log.info("Notification worker attempted {} pending notification(s).", pending.size());
        }

        return pending;
    }

}
