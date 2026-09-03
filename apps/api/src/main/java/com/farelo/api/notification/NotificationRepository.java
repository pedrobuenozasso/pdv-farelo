package com.farelo.api.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    // Backs NotificationService#listPending()/#list(NotificationStatus) —
    // oldest first, same FIFO reasoning already used by
    // com.farelo.api.printing.PrintJobRepository#findByStatusOrderByCreatedAtAsc
    // and com.farelo.api.outbox.OutboxEventRepository#findByStatusOrderByCreatedAtAsc.
    // Exists now so a future worker (FARELO-112/113) has a ready-made query
    // to poll PENDING notifications with, without needing its own migration
    // of this repository.
    //
    // No JOIN FETCH needed here (unlike PrintJobRepository's equivalent
    // query): Notification has no @ManyToOne/lazy associations to eagerly
    // load — every field is a plain column, so a derived Spring Data query
    // is sufficient.
    List<Notification> findByStatusOrderByCreatedAtAsc(NotificationStatus status);

}
