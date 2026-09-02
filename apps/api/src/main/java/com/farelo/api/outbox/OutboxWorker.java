package com.farelo.api.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * <p><strong>Safe under concurrent worker instances (FARELO-063).</strong>
 * Today only one instance of this application runs, so this method never
 * actually races against a copy of itself. But nothing about a
 * {@code @Scheduled} poller guarantees that stays true forever — if the
 * backend ever scales to more than one instance behind a load balancer (or
 * even just two schedulers running concurrently for any other reason),
 * every instance runs this same method on its own 5s timer, against the
 * same table. Without protection, two instances could both {@code SELECT}
 * the same {@code PENDING} rows before either commits, and both would go on
 * to "process" — and mark {@code PROCESSED} — the very same events. {@link
 * OutboxEventRepository#findPendingForUpdateSkipLocked} closes that gap:
 * each call row-locks (`FOR UPDATE`) whatever it selects for the rest of
 * its transaction, and a concurrent call from another instance silently
 * skips (`SKIP LOCKED`) any row already locked elsewhere instead of
 * selecting it too. That doesn't stop two instances from polling at the
 * same time — it stops them from ever selecting the *same* rows, which is
 * what makes double-processing impossible. See {@code
 * OutboxEventRepositoryConcurrencyIntegrationTests} for a deterministic
 * proof against a real Postgres instance (not mocked/H2 — row locking is
 * exactly the kind of behavior that only a real database enforces).
 *
 * <p><strong>Batch size</strong>: {@code outbox.worker.batch-size}, default
 * 100 (see {@code application.yml}) — bounds how many rows a single call
 * can lock and process, so one execution can never hold an unbounded number
 * of row locks (and, incidentally, never spend an unbounded amount of time
 * in one transaction) regardless of how deep the {@code PENDING} queue
 * gets. A queue deeper than the batch size is simply drained over more
 * poll cycles, each locking up to {@code batchSize} more rows.
 *
 * <p><strong>Poll interval</strong>: {@code outbox.worker.poll-interval-ms},
 * default 5000 (see {@code application.yml}) — how often the {@code
 * @Scheduled} trigger below fires in production. Configurable (rather than
 * the literal {@code 5000} this started as) so a test exercising the real
 * Spring context can push it far out and call {@link
 * #processPendingEvents()} directly instead, without the background
 * trigger firing mid-test and racing the explicit call over the same rows
 * — see {@code OutboxWorkerBatchSizeIntegrationTests}, which needs an
 * exact count of what one direct call processed.
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
    private final int batchSize;

    public OutboxWorker(
            OutboxEventRepository outboxEventRepository,
            @Value("${outbox.worker.batch-size:100}") int batchSize) {
        this.outboxEventRepository = outboxEventRepository;
        this.batchSize = batchSize;
    }

    /**
     * Locks and drains up to {@code batchSize} {@code PENDING} events.
     * Returns the batch it just processed — mainly so tests can assert on
     * exactly what one call touched; production code (the {@code
     * @Scheduled} trigger) ignores the return value.
     */
    @Scheduled(fixedDelayString = "${outbox.worker.poll-interval-ms:5000}")
    @Transactional
    public List<OutboxEvent> processPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findPendingForUpdateSkipLocked(
                OutboxEventStatus.PENDING.name(), batchSize);

        for (OutboxEvent event : pending) {
            log.info("Outbox event {} processed: {} on {} {} (stub — no real consumer yet, see class javadoc)",
                    event.getId(), event.getEventType(), event.getAggregateType(), event.getAggregateId());
            event.markProcessed();
        }

        if (!pending.isEmpty()) {
            outboxEventRepository.saveAll(pending);
        }

        return pending;
    }

}
