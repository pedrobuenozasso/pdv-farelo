package com.farelo.api.notification;

import java.util.UUID;

/**
 * Thrown when an operation references a {@link Notification} {@code id}
 * that does not exist. Same pattern as {@code
 * com.farelo.api.printing.PrintJobNotFoundException} — keyed by the
 * technical {@code id} (UUID), the only way a {@code Notification} is ever
 * looked up (it has no business-facing sequential identifier). New in this
 * ticket (FARELO-111): the read-only {@code GET /api/v1/notifications}
 * (FARELO-110) never looks a single notification up by id, so nothing
 * needed this until {@code POST /api/v1/notifications/{id}/send} did.
 */
public class NotificationNotFoundException extends RuntimeException {

    private final UUID notificationId;

    public NotificationNotFoundException(UUID notificationId) {
        super("Notification not found: " + notificationId);
        this.notificationId = notificationId;
    }

    public UUID getNotificationId() {
        return notificationId;
    }

}
