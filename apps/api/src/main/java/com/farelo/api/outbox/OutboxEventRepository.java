package com.farelo.api.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    // Backs OutboxWorker#processPendingEvents' polling loop — oldest
    // first, same FIFO reasoning as OrderRepository's queue query.
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status);

}
