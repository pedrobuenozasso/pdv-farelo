package com.farelo.api.outbox;

import com.farelo.api.notification.NotificationType;
import com.farelo.api.notification.OrderReadyNotificationService;
import com.farelo.api.notification.StockThresholdNotificationService;
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
 * starts here.
 *
 * <p><strong>Third real consumer (FARELO-113).</strong> A {@code STOCK_LOW}
 * or {@code OUT_OF_STOCK} event (published by {@code
 * com.farelo.api.inventory.InventoryMovementService#publishStockThresholdEventIfNeeded})
 * now results in {@link StockThresholdNotificationService#createForThresholdEvent
 * a PENDING Notification being created} — of the matching {@link
 * NotificationType}, addressed to the configured {@code
 * notification.internal-alert-recipient} — the "notificações internas:
 * estoque baixo, estoque zerado" flow from the prompt mestre (seção 19)
 * starts here. See that service's own javadoc for why its content is built
 * from the event's payload directly rather than a re-fetched {@code
 * Ingredient}, and why an unconfigured recipient is treated the same way a
 * missing {@code customerPhone} is above (skip, don't fail the batch).
 * Every other event type remains a no-op (see {@link #dispatch(OutboxEvent)}).
 *
 * <h2>Dispatch mechanism</h2>
 *
 * {@link #dispatch(OutboxEvent)} is a plain {@code if}/{@code else if} chain
 * on {@code event.getEventType()}, not a pluggable handler registry (e.g. a
 * {@code Map<String, OutboxEventHandler>} looked up by event type). This
 * stayed a YAGNI call after gaining a second branch at FARELO-112, on the
 * explicit promise that a third event type or consumer would be the point to
 * revisit it with a real registry. FARELO-113 is that point, materialized
 * concretely rather than hypothetically — and revisiting it for real (not
 * just restating the earlier promise) is the honest thing to do here.
 *
 * <p>Having actually built it, the concrete shape doesn't change the
 * conclusion: {@link #dispatch(OutboxEvent)} below is four straight-line
 * {@code if} branches (one per outbox {@code eventType}, since {@code
 * STOCK_LOW}/{@code OUT_OF_STOCK} still each get their own branch — see
 * below), calling into three consumers total. A registry would still need
 * to answer the same questions this codebase's actual usage still only has
 * one answer for: exactly one handler per event type (never several
 * subscribing to the same type), always synchronous, no retry policy shared
 * across handlers, no partial-failure behavior independent of the
 * whole-batch-rollback story below. Nothing about crossing from two
 * consumers to three changed any of that — <strong>the original
 * heuristic ("revisit at a third consumer") turns out to have been the
 * wrong trigger to watch</strong>: raw branch count was never actually
 * predictive of when a registry starts paying for itself; what would
 * actually justify one is one of those questions above gaining a second,
 * competing answer (e.g. two handlers legitimately needing to react to the
 * same event type, or one handler needing real per-event retry/backoff —
 * which is explicitly FARELO-079's territory, not this one's). That hasn't
 * happened at three consumers and there's no reason to expect it to happen
 * at four just because a bigger number was reached. This codebase's own
 * discipline (see e.g. {@code criticalStock} deferred twice in {@code
 * inventory}) is to build the abstraction when a second concrete case
 * actually needs it, not preemptively — the count-based promise above
 * didn't live up to that discipline as well as it seemed to at the time it
 * was written, so it's corrected here rather than mechanically honored.
 *
 * <p>One thing this ticket's new branch <em>does</em> demonstrate, worth
 * flagging for whoever eventually does build a registry: {@link
 * StockThresholdNotificationService#createForThresholdEvent(NotificationType, String)}
 * needs two arguments derived from the event ({@code NotificationType} and
 * the raw payload), unlike {@code printJobService.createForOrder(UUID)}/
 * {@code orderReadyNotificationService.createForOrder(UUID)} which both only
 * ever need {@code event.getAggregateId()}. A future registry's handler
 * interface should probably take the whole {@link OutboxEvent}, not just an
 * aggregate id, so each handler can pull whatever subset of it (id, type,
 * payload) it actually needs — this ticket didn't design that interface
 * (see above, still not warranted), but it's a real, concrete data point
 * for whenever one is.
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
 *
 * <p><strong>{@code initialDelayString}, same property — found in review, a
 * genuine bug, not part of any numbered ticket's original scope</strong>:
 * {@code @Scheduled(fixedDelayString = ...)} with no explicit initial delay
 * fires its <em>first</em> execution immediately on scheduler startup,
 * before the configured interval ever applies — the "disabled" 3600000ms
 * override from {@code AbstractIntegrationTest} only bounded the gap
 * <em>between</em> executions, not the first one. Every fresh Spring
 * context created during a test run (and this suite creates many — each
 * unique {@code @DynamicPropertySource} value combination, e.g. a
 * per-test-class local HTTP stub port, forces its own context) therefore
 * ran one real, un-suppressed {@code processPendingEvents()} call the
 * instant that context finished starting up, draining whatever was
 * {@code PENDING} in the shared singleton Postgres container at that exact
 * moment — including rows a still-running test in a <em>different</em>
 * context had just inserted. Reproduced concretely against {@code
 * OutboxMetricsIntegrationTests}: its own event, inserted and backdated by
 * the test body, was found already {@code PROCESSED} — by a thread named
 * {@code scheduling-1}, i.e. a background scheduler, not the test's own
 * thread — before the test read the gauge, only under the full suite
 * (never in isolation, where this same race resolves too fast after
 * context startup to matter). Setting {@code initialDelayString} to the
 * same property closes the gap: the first execution now waits just as long
 * as every subsequent one, so the 3600000ms test override actually
 * suppresses <em>every</em> execution for the lifetime of a test JVM, not
 * just the second one onward. {@link
 * com.farelo.api.notification.NotificationWorker} carries the identical
 * fix for the identical reason — see its own javadoc.
 */
@Component
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

    // The event types with a real consumer today — see class javadoc,
    // "Dispatch mechanism", for why this is a plain if/else-if chain rather
    // than a registered-handler lookup.
    private static final String ORDER_CREATED_EVENT_TYPE = "OrderCreated";
    private static final String ORDER_READY_EVENT_TYPE = "OrderReady";
    private static final String STOCK_LOW_EVENT_TYPE = "STOCK_LOW";
    private static final String OUT_OF_STOCK_EVENT_TYPE = "OUT_OF_STOCK";

    private final OutboxEventRepository outboxEventRepository;
    private final PrintJobService printJobService;
    private final OrderReadyNotificationService orderReadyNotificationService;
    private final StockThresholdNotificationService stockThresholdNotificationService;
    private final int batchSize;

    public OutboxWorker(
            OutboxEventRepository outboxEventRepository,
            PrintJobService printJobService,
            OrderReadyNotificationService orderReadyNotificationService,
            StockThresholdNotificationService stockThresholdNotificationService,
            @Value("${outbox.worker.batch-size:100}") int batchSize) {
        this.outboxEventRepository = outboxEventRepository;
        this.printJobService = printJobService;
        this.orderReadyNotificationService = orderReadyNotificationService;
        this.stockThresholdNotificationService = stockThresholdNotificationService;
        this.batchSize = batchSize;
    }

    /**
     * Locks and drains up to {@code batchSize} {@code PENDING} events.
     * Returns the batch it just processed — mainly so tests can assert on
     * exactly what one call touched; production code (the {@code
     * @Scheduled} trigger) ignores the return value.
     */
    @Scheduled(
            fixedDelayString = "${outbox.worker.poll-interval-ms:5000}",
            initialDelayString = "${outbox.worker.poll-interval-ms:5000}")
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
    // type other than the four below is a no-op today — there is nothing
    // else to dispatch to yet.
    private void dispatch(OutboxEvent event) {
        if (ORDER_CREATED_EVENT_TYPE.equals(event.getEventType())) {
            printJobService.createForOrder(event.getAggregateId());
        } else if (ORDER_READY_EVENT_TYPE.equals(event.getEventType())) {
            orderReadyNotificationService.createForOrder(event.getAggregateId());
        } else if (STOCK_LOW_EVENT_TYPE.equals(event.getEventType())) {
            stockThresholdNotificationService.createForThresholdEvent(NotificationType.STOCK_LOW, event.getPayload());
        } else if (OUT_OF_STOCK_EVENT_TYPE.equals(event.getEventType())) {
            stockThresholdNotificationService.createForThresholdEvent(
                    NotificationType.OUT_OF_STOCK, event.getPayload());
        }
    }

}
