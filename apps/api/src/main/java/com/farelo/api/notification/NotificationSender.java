package com.farelo.api.notification;

import com.farelo.api.notification.whatsapp.WhatsAppClient;
import com.farelo.api.notification.whatsapp.WhatsAppSendException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The actual outbound-sending mechanism for a {@link Notification}
 * (FARELO-111) — takes an already-existing notification, attempts delivery
 * via {@link WhatsAppClient}, and marks it {@code SENT}/{@code FAILED}
 * accordingly. This is deliberately <strong>only</strong> the send-and-
 * record-outcome cycle, not a trigger: nothing here decides <em>when</em> a
 * notification gets sent or constructs one in the first place — reacting to
 * {@code ORDER_READY} (FARELO-112) and "estoque baixo" (FARELO-113) are
 * both future tickets that will call {@link #send(Notification)} (or {@link
 * #sendById(UUID)}) once they exist, the same way this ticket's own manual
 * {@code POST /api/v1/notifications/{id}/send} endpoint (see {@code
 * NotificationController}) does today for operability/testability. See
 * {@code Notification}'s javadoc and docs/domain-model.md's {@code
 * notification} section for the full scope boundary.
 *
 * <h2>Why a separate class from {@code NotificationService}</h2>
 *
 * {@link NotificationService} is documented (FARELO-110) as read-only —
 * "no write methods yet — nothing in this ticket creates or transitions a
 * {@code Notification}". Rather than editing that class to bolt write
 * behavior onto a class whose own javadoc asserts it has none, this ticket
 * adds a new, narrowly-scoped class: one reason to change ({@code
 * NotificationService} changes if the read/listing shape changes; {@code
 * NotificationSender} changes if the delivery mechanism changes), and it
 * keeps the diff for FARELO-111 additive rather than rewriting a class two
 * tickets already relied on staying read-only.
 *
 * <h2>No status-transition validation here</h2>
 *
 * Unlike, say, {@code PrintJobService#markPrinted}/{@code #markFailed}
 * (which reject transitioning anything but a {@code PENDING} job), {@link
 * #send(Notification)} does not check {@link Notification#getStatus()}
 * before attempting delivery — it will happily re-attempt an already-{@code
 * SENT} or already-{@code FAILED} notification if asked. This mirrors
 * {@link Notification#markSent()}/{@link Notification#markFailed()}
 * themselves being documented as unvalidated, "no real caller yet to
 * validate a transition rule against" (see their javadoc) — this ticket
 * <em>is</em> that first real caller, but still has no concrete second use
 * case to design a transition policy against: FARELO-112/113 (the only
 * planned future callers) will only ever hand this a freshly-created {@code
 * PENDING} notification, and the one caller inside this ticket (the manual
 * admin endpoint) exists specifically so an operator can force a resend —
 * rejecting that on a non-{@code PENDING} notification would work against
 * its own purpose. Guessing at a validation rule now, with no real
 * conflicting caller to guard against, would be the same speculative
 * structure {@code NotificationStatus}'s javadoc already declined to add.
 */
@Service
public class NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

    private final NotificationRepository notificationRepository;
    private final WhatsAppClient whatsAppClient;

    public NotificationSender(NotificationRepository notificationRepository, WhatsAppClient whatsAppClient) {
        this.notificationRepository = notificationRepository;
        this.whatsAppClient = whatsAppClient;
    }

    /**
     * Attempts delivery of {@code notification} via {@link WhatsAppClient}
     * and persists the outcome: {@link Notification#markSent()} on success,
     * {@link Notification#markFailed()} if {@link WhatsAppClient} throws
     * {@link WhatsAppSendException}. Never lets that exception propagate to
     * the caller — same "no failure crashes the process" contract {@link
     * com.farelo.api.notification.whatsapp.WhatsAppCloudApiClient}'s own
     * javadoc documents one layer down, preserved here so a future
     * scheduler/worker (FARELO-112/113) can call this in a loop over many
     * notifications without one bad recipient/timeout aborting the batch.
     *
     * @return the same notification, saved with its updated status.
     */
    @Transactional
    public Notification send(Notification notification) {
        try {
            whatsAppClient.sendTextMessage(notification.getRecipient(), notification.getContent());
            notification.markSent();
        } catch (WhatsAppSendException e) {
            log.warn("Failed to send notification {} ({}) to {}: {}",
                    notification.getId(), notification.getType(), notification.getRecipient(), e.getMessage());
            notification.markFailed();
        }
        return notificationRepository.save(notification);
    }

    /**
     * Looks {@code id} up and delegates to {@link #send(Notification)} —
     * the shape {@code POST /api/v1/notifications/{id}/send} needs.
     *
     * @throws NotificationNotFoundException if no notification exists for
     *         {@code id}.
     */
    @Transactional
    public Notification sendById(UUID id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        return send(notification);
    }

}
