package com.farelo.api.audit;

import com.farelo.api.security.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * FARELO-125: read access to {@link AuditLog} (backs {@code GET
 * /api/v1/audit-logs}) plus {@link #record}, the one write method this
 * domain has. <b>Nothing in this ticket calls {@link #record}</b> — it
 * exists now as the ready-made entry point future producers (FARELO-126
 * "auditar alteração de preço", FARELO-127 "auditar ajuste de estoque") are
 * expected to call from inside {@code ProductService}/{@code
 * InventoryMovementService}, the same "build the seam now, wire the call
 * site later" shape already used by {@code NotificationRepository}'s
 * {@code findByStatusOrderByCreatedAtAsc} at FARELO-110 (a query with no
 * caller until FARELO-112/113 arrived). This ticket deliberately does not
 * touch either of those two services.
 */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Records one audit entry. Takes an already-loaded {@link User} — the
     * acting admin — rather than an id, name and email spread across three
     * parameters: a future producer already has (or has just looked up) the
     * {@code User} behind the request (e.g. resolving {@code
     * AuthenticatedPrincipal#userId()} once RBAC — FARELO-122/123 — actually
     * guards the write endpoints these future tickets touch), so this method
     * does the one thing only it should be responsible for — unpacking the
     * name/email snapshot at the moment of the call — rather than pushing
     * that unpacking onto every caller. See {@link AuditLog}'s javadoc
     * ("Design decision 2") for why the entity itself stores that snapshot
     * by value instead of a live {@code @ManyToOne}.
     *
     * <p>{@code previousValue}/{@code newValue} are pre-serialized JSON
     * strings (or {@code null}), same division of labor {@code
     * PrintJobService} already has for {@code PrintJob#content} — this
     * method has no opinion on their shape, only stores what it's given (see
     * {@link AuditLog}'s javadoc, "Design decision 4").
     */
    @Transactional
    public AuditLog record(
            User actor, String action, String entityType, UUID entityId, String previousValue, String newValue) {
        AuditLog auditLog = new AuditLog(
                actor.getId(), actor.getName(), actor.getEmail(),
                action, entityType, entityId, previousValue, newValue);
        return auditLogRepository.save(auditLog);
    }

    /**
     * Backs {@code GET /api/v1/audit-logs}. Three optional, independent
     * filters — {@code userId} takes priority if given (see below), then
     * {@code entityType}+{@code entityId} together, otherwise every row:
     *
     * <ul>
     *   <li>{@code entityType}/{@code entityId} apply only when <b>both</b>
     *       are given — a partial pair (only one of the two) is ambiguous
     *       (an id alone isn't unique across entity types; a type alone
     *       against every row of that type is a much bigger, differently-
     *       shaped query this first cut doesn't build) and is treated as if
     *       neither were given, falling through to the unfiltered list
     *       rather than guessing at what a lone value means.</li>
     *   <li>{@code userId} is independent and takes priority when present:
     *       "what did this user do" is a real question a filtered-by-entity
     *       query can't answer, so it isn't combined with the entity filter
     *       above (a first cut has no query to intersect the two — see class
     *       javadoc's YAGNI note).</li>
     * </ul>
     *
     * <p>Ordered newest-first ({@code createdAt DESC}) — a deliberate
     * divergence from {@code NotificationService#list}'s oldest-first FIFO
     * (a queue meant to be drained in order). An audit trail has no queue to
     * drain; a human reviewing it almost always wants "what just happened",
     * the same reason a version-control log or a chat scrollback shows
     * newest first.
     */
    @Transactional(readOnly = true)
    public List<AuditLog> list(String entityType, UUID entityId, UUID userId) {
        if (userId != null) {
            return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }
        if (entityType != null && entityId != null) {
            return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
        }
        return auditLogRepository.findAllByOrderByCreatedAtDesc();
    }

}
