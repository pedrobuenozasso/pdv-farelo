package com.farelo.api.outbox;

import com.farelo.api.notification.OrderReadyNotificationService;
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
 * the prompt mestre (seção 10) starts here.
 *
 * <p><strong>Second real consumer (FARELO-112).</strong> An {@code
 * OrderReady} event (published by {@code
 * com.farelo.api.ordering.OrderService#markAsReady}) now results in {@link
 * OrderReadyNotificationService#createForOrder a PENDING Notification being
 * created} for that order's customer (or nothing at all, if the order has no
 * {@code customerPhone} — see that class's javadoc) — the {@code ORDER_READY
 * → Notification Worker → WhatsApp} flow from the prompt mestre (seção 19)
 * starts here. Every other event type remains a no-op (see {@link
 * #dispatch(OutboxEvent)}): inventory, the last remaining eventual reader of
 * outbox events named in seção 29, is still a future epic that hasn't
 * started.
 *
 * <h2>Dispatch mechanism</h2>
 *
 * {@link #dispatch(OutboxEvent)} is a plain {@code if}/{@code else if} chain
 * on {@code event.getEventType()}, not a pluggable handler registry (e.g. a
 * {@code Map<String, OutboxEventHandler>} looked up by event type). This
 * stayed a YAGNI call even after gaining a second branch here (FARELO-112):
 * two straight-line {@code if} checks, each calling exactly one method on
 * exactly one consumer, is still simpler to read than a registry indirection
 * would be, and neither branch has any behavior in common to factor out (no
 * shared retry policy, no multiple handlers per event type, nothing a
 * registry would actually buy today). The questions a real registry would
 * need to answer — one handler per event type, or several subscribing to the
 * same type? synchronous or queued? how do partial failures across handlers
 * behave, independent of the whole-batch-rollback story below? — still have
 * only one concrete answer each in this codebase's actual usage, not two
 * competing ones to design against. <strong>This should become a real
 * registered-handler mechanism once a third event type or consumer shows
 * up</strong> (e.g. inventory reacting to {@code OrderCreated}, or {@code
 * STOCK_LOW}/{@code STOCK_CRITICAL}/{@code OUT_OF_STOCK} feeding {@code
 * notification} the way {@code OrderReady} does here — both FARELO-113 and
 * beyond) — at that point there would be three real cases (and likely a
 * repeated shape between at least two of them) to design the abstraction
 * against, instead of extrapolating from two.
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

    // The two event types with a real consumer today — see class javadoc,
    // "Dispatch mechanism", for why this is a plain if/else-if chain rather
    // than a registered-handler lookup.
    private static final String ORDER_CREATED_EVENT_TYPE = "OrderCreated";
    private static final String ORDER_READY_EVENT_TYPE = "OrderReady";

    private final OutboxEventRepository outboxEventRepository;
    private final PrintJobService printJobService;
    private final OrderReadyNotificationService orderReadyNotificationService;
    private final int batchSize;

    public OutboxWorker(
            OutboxEventRepository outboxEventRepository,
            PrintJobService printJobService,
            OrderReadyNotificationService orderReadyNotificationService,
            @Value("${outbox.worker.batch-size:100}") int batchSize) {
        this.outboxEventRepository = outboxEventRepository;
        this.printJobService = printJobService;
        this.orderReadyNotificationService = orderReadyNotificationService;
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
    // if/else-if chain instead of a registered-handler lookup. Any event
    // type other than the two below is a no-op today — there is nothing
    // else to dispatch to yet.
    private void dispatch(OutboxEvent event) {
        if (ORDER_CREATED_EVENT_TYPE.equals(event.getEventType())) {
            printJobService.createForOrder(event.getAggregateId());
        } else if (ORDER_READY_EVENT_TYPE.equals(event.getEventType())) {
            orderReadyNotificationService.createForOrder(event.getAggregateId());
        }
    }

}
