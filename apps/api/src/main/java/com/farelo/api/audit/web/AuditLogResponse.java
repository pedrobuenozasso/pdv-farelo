package com.farelo.api.audit.web;

import com.farelo.api.audit.AuditLog;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body for {@code GET /api/v1/audit-logs} (FARELO-125) — the JPA
 * entity is never exposed directly (AGENTS.md). Every field is a plain
 * value on {@link AuditLog} already (no lazy association to resolve), so
 * this is a straight field-for-field copy — same shape as {@code
 * NotificationResponse}.
 */
public record AuditLogResponse(
        UUID id,
        UUID userId,
        String userName,
        String userEmail,
        String action,
        String entityType,
        UUID entityId,
        String previousValue,
        String newValue,
        OffsetDateTime createdAt) {

    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getUserId(),
                auditLog.getUserName(),
                auditLog.getUserEmail(),
                auditLog.getAction(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getPreviousValue(),
                auditLog.getNewValue(),
                auditLog.getCreatedAt());
    }

}
