package com.farelo.api.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    // Backs AuditLogService#list when no filter is given — every audit row,
    // newest first (see AuditLogService#list's javadoc for why newest-first,
    // unlike NotificationRepository's oldest-first FIFO convention).
    List<AuditLog> findAllByOrderByCreatedAtDesc();

    // Backs AuditLogService#list's entityType+entityId filter — the audit
    // trail of one specific record (e.g. "everything ever changed about
    // Product X"), the single most obvious real query an audit log exists
    // to answer.
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId);

    // Backs AuditLogService#list's userId filter — everything one user did.
    List<AuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId);

}
