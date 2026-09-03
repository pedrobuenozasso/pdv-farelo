package com.farelo.api.notification;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-only access to {@link Notification}s (FARELO-110). No write methods
 * yet — nothing in this ticket creates or transitions a {@code
 * Notification} (see that entity's javadoc for the full scope rationale);
 * this class exists only to back {@code GET /api/v1/notifications} and to
 * give a future worker (FARELO-112/113) a ready-made {@link
 * #listPending()} to poll, the same shape {@code
 * com.farelo.api.printing.PrintJobService#listPending()} already
 * established.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Lists every {@code PENDING} notification, oldest first (FIFO) — same
     * reasoning as {@code PrintJobService#listPending()}. Not called by
     * anything in this ticket (no worker exists yet to consume it), but
     * kept as the query a future one (FARELO-112/113) will need.
     */
    @Transactional(readOnly = true)
    public List<Notification> listPending() {
        return notificationRepository.findByStatusOrderByCreatedAtAsc(NotificationStatus.PENDING);
    }

    /**
     * Backs {@code GET /api/v1/notifications}: every notification matching
     * {@code status} (oldest first), or, when {@code status} is {@code
     * null}, every notification regardless of status (still oldest first —
     * same ordering either way, so a caller switching between filtered and
     * unfiltered calls sees a consistent order, not two different
     * conventions).
     */
    @Transactional(readOnly = true)
    public List<Notification> list(NotificationStatus status) {
        if (status != null) {
            return notificationRepository.findByStatusOrderByCreatedAtAsc(status);
        }
        return notificationRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt"));
    }

}
