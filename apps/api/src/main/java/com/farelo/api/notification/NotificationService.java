package com.farelo.api.notification;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-only access to {@link Notification}s (FARELO-110). No write methods
 * — creating/transitioning a {@code Notification} lives in {@link
 * OrderReadyNotificationService}/{@link NotificationSender} instead (see
 * {@code NotificationSender}'s javadoc, "Why a separate class from {@code
 * NotificationService}", for why this class stayed read-only rather than
 * growing those responsibilities itself). Backs {@code GET
 * /api/v1/notifications} and, since FARELO-112, {@link NotificationWorker}'s
 * poll loop via {@link #listPending()} — the same shape {@code
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
     * reasoning as {@code PrintJobService#listPending()}. Polled by {@link
     * NotificationWorker} (FARELO-112), its first real caller — see that
     * class's javadoc for the send loop built on top of this query.
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
