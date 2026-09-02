package com.farelo.api.outbox;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the FARELO-063 concurrency guarantee behind {@link
 * OutboxEventRepository#findPendingForUpdateSkipLocked}: two transactions
 * running this query at the same time never lock — and therefore never
 * process — the same {@code PENDING} row. This is the scenario {@code
 * OutboxWorker} would face if the backend ever ran as more than one
 * instance (see that class's javadoc): without {@code FOR UPDATE SKIP
 * LOCKED}, two concurrent pollers could both select and "process" the exact
 * same event.
 *
 * <p><strong>Deterministic, not a timing race.</strong> A naive version of
 * this test would fire two threads at roughly the same time and hope they
 * overlap — flaky by construction, since a fast thread can finish its whole
 * transaction before the other even starts. Instead, this test drives two
 * transactions by hand with {@link TransactionTemplate} and two {@link
 * CountDownLatch}es so the interleaving is exact on every run:
 *
 * <ol>
 *   <li>Transaction A selects (and locks) every {@code PENDING} row —
 *       including all of this test's seeded events — and then blocks,
 *       transaction still open and uncommitted.</li>
 *   <li>Only once A is confirmed to be holding those locks does transaction
 *       B run the exact same select.</li>
 *   <li>B's result is captured and asserted before A is allowed to commit —
 *       so there is no window in which A could have already released its
 *       locks and let B see (or grab) the same rows.</li>
 * </ol>
 *
 * <p>Runs against the real Postgres container from {@link
 * AbstractIntegrationTest} (Testcontainers), not H2 or a mock — row locking
 * (`FOR UPDATE SKIP LOCKED`) is real database engine behavior with no
 * in-memory equivalent worth trusting; this is exactly the kind of bug that
 * only shows up against the real thing.
 *
 * <p>Assertions are scoped to this test's own seeded event ids throughout
 * (never a raw count of everything {@code PENDING}) — {@code outbox_event}
 * is shared across every test class against the singleton Postgres
 * container, same defensive style already used by {@code
 * OutboxMetricsIntegrationTests}.
 */
@SpringBootTest
class OutboxEventRepositoryConcurrencyIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<UUID> savedEventIds = new ArrayList<>();

    @AfterEach
    void deleteTestEvents() {
        for (UUID id : savedEventIds) {
            outboxEventRepository.findById(id).ifPresent(outboxEventRepository::delete);
        }
        savedEventIds.clear();
    }

    @Test
    void concurrentTransactionsNeverLockTheSamePendingEventSimultaneously() throws Exception {
        int eventCount = 6;
        Set<UUID> seededIds = seedPendingEvents(eventCount);

        TransactionTemplate txA = new TransactionTemplate(transactionManager);
        TransactionTemplate txB = new TransactionTemplate(transactionManager);

        CountDownLatch aHasLocked = new CountDownLatch(1);
        CountDownLatch bHasSelected = new CountDownLatch(1);

        List<UUID> resultA = new ArrayList<>();
        List<UUID> resultB = new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Transaction A: locks every PENDING row (limit far above what
            // this test could ever seed), signals it holds the locks, then
            // blocks — still inside the transaction, nothing committed —
            // until B has run its own select against those same rows.
            Future<?> futureA = executor.submit(() -> txA.executeWithoutResult(status -> {
                List<OutboxEvent> locked = outboxEventRepository.findPendingForUpdateSkipLocked(
                        OutboxEventStatus.PENDING.name(), 1000);
                resultA.addAll(locked.stream().map(OutboxEvent::getId).toList());

                aHasLocked.countDown();
                awaitOrFail(bHasSelected, "B to finish its select while A still holds its locks");

                // Only mark this test's own seeded events processed —
                // leaves any unrelated row this happened to also lock
                // (there shouldn't be one, given every other test class
                // cleans up its own PENDING rows, but this test doesn't
                // rely on that) untouched; its lock is released harmlessly
                // when A commits below.
                List<OutboxEvent> ours = locked.stream().filter(e -> seededIds.contains(e.getId())).toList();
                ours.forEach(OutboxEvent::markProcessed);
                outboxEventRepository.saveAll(ours);
            }));

            // Transaction B: waits until A is confirmed to hold its locks,
            // then runs the identical select. SKIP LOCKED means it must
            // silently skip every row A already locked instead of blocking
            // on them or selecting them anyway.
            Future<?> futureB = executor.submit(() -> {
                awaitOrFail(aHasLocked, "A to lock its rows");
                txB.executeWithoutResult(status -> {
                    List<OutboxEvent> locked = outboxEventRepository.findPendingForUpdateSkipLocked(
                            OutboxEventStatus.PENDING.name(), 1000);
                    resultB.addAll(locked.stream().map(OutboxEvent::getId).toList());
                    bHasSelected.countDown();
                    // Nothing of ours to process — A already holds every
                    // one of our rows locked, asserted below.
                });
            });

            futureA.get(10, TimeUnit.SECONDS);
            futureB.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        // A, running against an otherwise-quiescent table, locked every one
        // of this test's seeded events.
        assertThat(resultA).containsAll(seededIds);
        // B ran its select while A's transaction was still open and had not
        // yet committed — SKIP LOCKED guarantees none of A's locked rows,
        // ours included, could appear in B's result. This is the core
        // idempotency guarantee: two concurrent worker instances can never
        // both select (and therefore never both process) the same event.
        assertThat(resultB).doesNotContainAnyElementsOf(seededIds);

        List<OutboxEvent> reloaded = outboxEventRepository.findAllById(seededIds);
        assertThat(reloaded).hasSize(eventCount);
        assertThat(reloaded).allSatisfy(event -> {
            assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSED);
            assertThat(event.getProcessedAt()).isNotNull();
        });
    }

    private Set<UUID> seedPendingEvents(int count) {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < count; i++) {
            UUID id = outboxEventRepository.saveAndFlush(
                    new OutboxEvent("ConcurrencyTest", UUID.randomUUID(), "TestEvent", "{}")).getId();
            ids.add(id);
            savedEventIds.add(id);
        }
        return ids;
    }

    private static void awaitOrFail(CountDownLatch latch, String waitingFor) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for " + waitingFor);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted waiting for " + waitingFor, e);
        }
    }

}
