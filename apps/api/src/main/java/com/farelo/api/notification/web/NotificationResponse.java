package com.farelo.api.notification.web;

import com.farelo.api.notification.Notification;
import com.farelo.api.notification.NotificationStatus;
import com.farelo.api.notification.NotificationType;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body for {@code GET /api/v1/notifications} (FARELO-110) — the
 * JPA entity is never exposed directly (see AGENTS.md). Unlike {@code
 * com.farelo.api.printing.web.PrintJobResponse}, {@code content} is served
 * as-is (a plain string): {@link Notification#getContent()} is already
 * plain text, not a serialized JSON snapshot, so there is nothing to
 * deserialize here (see {@code Notification}'s javadoc, "Design decision
 * 3").
 */
public record NotificationResponse(
        UUID id,
        NotificationType type,
        String recipient,
        String content,
        NotificationStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getRecipient(),
                notification.getContent(),
                notification.getStatus(),
                notification.getCreatedAt(),
                notification.getUpdatedAt());
    }

}
