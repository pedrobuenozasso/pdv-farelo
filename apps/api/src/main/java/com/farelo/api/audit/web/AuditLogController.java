package com.farelo.api.audit.web;

import com.farelo.api.audit.AuditLogService;
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
 * <p><b>Deliberately unprotected — no {@code @RequireRole}</b>, following
 * this codebase's established precedent of deferring "who can call this" to
 * a dedicated RBAC-application ticket rather than bundling it into an
 * unrelated feature ticket (see docs/domain-model.md, FARELO-123's
 * subsection under {@code security}, and {@code
 * RoleAuthorizationInterceptorRegressionIntegrationTests}, which explicitly
 * proves {@code GET /api/v1/notifications} — the same first-cut shape this
 * endpoint follows — stayed unprotected through FARELO-122/123). A future
 * RBAC-application ticket, once one targets the {@code audit} domain, is the
 * right place to decide which roles (plausibly {@code ADMIN} only — this is
 * arguably more sensitive than {@code GET /api/v1/users}, which FARELO-123
 * restricted to {@code ADMIN}/{@code MANAGER}) may read the audit trail; a
 * standalone feature ticket that only builds the entity/repository/read
 * endpoint has no request-side identity concept to decide that against yet
 * on its own.
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
    public List<AuditLogResponse> list(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) UUID userId) {
        return auditLogService.list(entityType, entityId, userId).stream()
                .map(AuditLogResponse::from)
                .toList();
    }

}
