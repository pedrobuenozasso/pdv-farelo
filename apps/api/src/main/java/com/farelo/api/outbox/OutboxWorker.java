package com.farelo.api.outbox;

import com.farelo.api.printing.PrintJobService;
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
 * <p><strong>First real consumer (FARELO-072).</strong> An {@code
 * OrderCreated} event now results in a {@link PrintJobService#createForOrder
 * PrintJob being created} for that order — the {@code Order criado →
 * PrintJob PENDING → Farelo Edge Agent → impressora → PRINTED} flow from
 * the prompt mestre (seção 10) starts here. Every other event type is
 * still a no-op (see {@link #dispatch(OutboxEvent)}): notification and
 * inventory, the other eventual readers of outbox events, are future
 * epics that haven't started.
 *
 * <h2>Dispatch mechanism</h2>
 *
 * {@link #dispatch(OutboxEvent)} is a plain {@code if} on {@code
 * event.getEventType()}, not a pluggable handler registry (e.g. a {@code
 * Map<String, OutboxEventHandler>} looked up by event type). That's a
 * deliberate YAGNI call, not an oversight: with exactly one event type
 * ({@code OrderCreated}) and one consumer (printing), a registry would be
 * an abstraction with a single entry — there's no second case yet to prove
 * the right shape against (would it dispatch to one handler per event
 * type, or let several subscribe to the same type? synchronously or
 * queued? how do partial failures across handlers behave?). Answering
 * those questions now means guessing. <strong>This should become a real
 * registered-handler mechanism once a second event type or consumer shows
 * up</strong> (e.g. inventory reacting to {@code OrderCreated} too, or a
 * new event type entirely for notifications — both future epics) — at that
 * point there are two real cases to design the abstraction against instead
 * of one imagined one.
 *
 * <h2>Failure handling (FARELO-072)</h2>
 *
 * {@link #dispatch(OutboxEvent)} runs inside this method's
 * {@code @Transactional} boundary, before the dispatched event is marked
 * {@code PROCESSED}. If it throws (e.g. {@code PrintJobService} can't find
 * the order — not expected in practice, since the order was written in the
 * same transaction that published the event, but not guarded against
 * beyond this), the exception propagates out of {@link
 * #processPendingEvents()} and the whole method's transaction rolls back —
 * <strong>every</strong> event in the current batch, not just the one that
 * failed, reverts to {@code PENDING} (nothing in it was ever committed as
 * {@code PROCESSED}). Locks taken by {@code FOR UPDATE} are released with
 * the rollback, so the next poll cycle (or another instance, per the
 * concurrency note below) picks the whole batch back up and retries it
 * unchanged.
 *
 * <p>This is a known, deliberate limitation, not a bug being hidden: a
 * genuinely broken event sitting first in a batch would make every poll
 * cycle re-attempt (and re-fail on) that same event before ever reaching
 * the healthy ones behind it in that batch, effectively stalling the
 * queue. Isolating one failing event from the rest of its batch (e.g.
 * catching per-event, marking a failed event some other way so it stops
 * blocking its neighbors, with real retry/backoff) is a sophisticated
 * mechanism this ticket deliberately does not build — that's FARELO-079
 * ("Criar retry de impressão"), scoped for when a real failure mode
 * (printer down, etc) actually needs it.
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
 */
@Component
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

    // The only event type with a real consumer today — see class javadoc,
    // "Dispatch mechanism", for why this is a plain if-check rather than a
    // registered-handler lookup.
    private static final String ORDER_CREATED_EVENT_TYPE = "OrderCreated";

    private final OutboxEventRepository outboxEventRepository;
    private final PrintJobService printJobService;
    private final int batchSize;

    public OutboxWorker(
            OutboxEventRepository outboxEventRepository,
            PrintJobService printJobService,
            @Value("${outbox.worker.batch-size:100}") int batchSize) {
        this.outboxEventRepository = outboxEventRepository;
        this.printJobService = printJobService;
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
            // See class javadoc, "Failure handling": a dispatch failure
            // here is deliberately left to propagate and roll back this
            // whole method's transaction, rather than being caught and
            // isolated per event.
            dispatch(event);
            log.info("Outbox event {} processed: {} on {} {}",
                    event.getId(), event.getEventType(), event.getAggregateType(), event.getAggregateId());
            event.markProcessed();
        }

        if (!pending.isEmpty()) {
            outboxEventRepository.saveAll(pending);
        }

        return pending;
    }

    // See class javadoc, "Dispatch mechanism", for why this is a plain
    // if-check instead of a registered-handler lookup. Any event type
    // other than OrderCreated is a no-op today — there is nothing else to
    // dispatch to yet.
    private void dispatch(OutboxEvent event) {
        if (ORDER_CREATED_EVENT_TYPE.equals(event.getEventType())) {
            printJobService.createForOrder(event.getAggregateId());
        }
    }

}
