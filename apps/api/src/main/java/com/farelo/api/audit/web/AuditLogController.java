package com.farelo.api.audit.web;

import com.farelo.api.audit.AuditLogService;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.rbac.RequireRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints of the {@code audit} domain. {@link #list} is the only
 * endpoint (FARELO-125) — minimal first cut, same pattern already used by
 * {@code NotificationController#list} (FARELO-110) and {@code
 * PrinterController}/{@code Printer} (FARELO-070) at their own first
 * tickets: read-only, no write endpoint. Nothing in this ticket produces a
 * real {@link com.farelo.api.audit.AuditLog} — see {@code
 * AuditLogService#record}'s javadoc.
 *
 * <p>The audit trail includes actor identities and before/after snapshots;
 * reading it requires {@code ADMIN}.
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    // Optional ?entityType=&entityId= (both together) and/or ?userId=
    // filters — see AuditLogService#list's javadoc for the exact
    // precedence/combination rules. Always 200 OK (a list, potentially
    // empty; no path parameter to validate) — same as GET
    // /api/v1/notifications.
    @GetMapping
    @RequireRole(UserRole.ADMIN)
    public List<AuditLogResponse> list(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) UUID userId) {
        return auditLogService.list(entityType, entityId, userId).stream()
                .map(AuditLogResponse::from)
                .toList();
    }

}
