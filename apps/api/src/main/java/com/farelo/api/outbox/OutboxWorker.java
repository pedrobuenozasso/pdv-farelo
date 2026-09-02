package com.farelo.api.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Polls {@code outbox_event} for {@code PENDING} rows and drains them
 * (FARELO-060 — see {@code com.farelo.api.outbox}'s package-info).
 *
 * <p><strong>Stub, by design.</strong> There is no real consumer yet:
 * printing, notification and inventory — the eventual readers of these
 * events — are future epics that haven't started. This method only logs
 * each pending event and marks it {@code PROCESSED}, which is enough to
 * prove the publish → poll → drain mechanism end to end (see {@code
 * OrderService#create}'s {@code OrderCreated} event for a real producer)
 * without inventing a dispatch mechanism nobody needs yet.
 *
 * <p><strong>Future extension point</strong>: when a real consumer shows
 * up, it plugs in around the loop below — most likely a handler registered
 * per {@code event_type} and dispatched from here instead of today's
 * log-and-mark-processed body. That dispatch shape is deliberately
 * <em>not</em> decided or built now (YAGNI) — there's only one "consumer"
 * (this stub) to design it against, which isn't enough information to get
 * it right.
 */
@Component
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

    private final OutboxEventRepository outboxEventRepository;

    public OutboxWorker(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);

        for (OutboxEvent event : pending) {
            log.info("Outbox event {} processed: {} on {} {} (stub — no real consumer yet, see class javadoc)",
                    event.getId(), event.getEventType(), event.getAggregateType(), event.getAggregateId());
            event.markProcessed();
        }

        if (!pending.isEmpty()) {
            outboxEventRepository.saveAll(pending);
        }
    }

}
