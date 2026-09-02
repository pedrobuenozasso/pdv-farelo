package com.farelo.api.outbox;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link OutboxWorker#processPendingEvents()} (FARELO-063) never
 * locks/processes more than {@code outbox.worker.batch-size} {@code
 * PENDING} rows in a single call — the safeguard against one worker
 * execution holding an unbounded number of row locks regardless of how deep
 * the queue is.
 *
 * <p>Own {@code @SpringBootTest} context, separate from {@link
 * OutboxWorkerIntegrationTests}: {@code @TestPropertySource} overrides
 * {@code outbox.worker.batch-size} for the whole context, and sharing a
 * context with tests that need the production default (100) would force a
 * choice between them.
 *
 * <p><strong>Why this doesn't assert an exact partition into two calls.</strong>
 * An earlier version seeded 5 events and asserted the first call returns
 * exactly 3 and the second exactly 2. That's flakier than it looks: Spring
 * Test caches {@code ApplicationContext}s across test classes for the whole
 * suite, and most other outbox test classes use the production defaults
 * (batch-size 100, poll-interval 5000ms) with no property override — their
 * cached context's real {@code @Scheduled} worker keeps running in the
 * background for the life of the test JVM, independent of what property
 * values *this* test's own context was given. Pushing out {@code
 * poll-interval-ms} here only silences *this* context's own trigger; it
 * can't stop an already-running worker from a different, already-cached
 * context from polling the same shared {@code outbox_event} table
 * (singleton Postgres, see {@link AbstractIntegrationTest}) and grabbing
 * some of this test's seeded rows between its two explicit calls — this
 * happened for real, not hypothetically.
 *
 * <p>So instead of asserting an exact split, this test asserts the
 * invariant that actually matters and holds regardless of who else touches
 * the table concurrently: <strong>no single {@link
 * OutboxWorker#processPendingEvents()} call, from any context, ever
 * returns more than {@code batchSize} events</strong> (checked on every
 * call this test makes), and <strong>every event this test seeds
 * eventually reaches {@code PROCESSED}</strong> — whether this test's own
 * calls drained it or a concurrent worker elsewhere did doesn't matter for
 * what this test is proving.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "outbox.worker.batch-size=3",
        "outbox.worker.poll-interval-ms=3600000"
})
class OutboxWorkerBatchSizeIntegrationTests extends AbstractIntegrationTest {

    private static final int BATCH_SIZE = 3;
    private static final int SEEDED_EVENT_COUNT = 5;
    // Generous bound on how many polls it can take to drain everything,
    // even accounting for unrelated concurrent activity from other cached
    // contexts also touching the table — each poll processes at least
    // BATCH_SIZE of *something*, so this comfortably covers seeding 5 of
    // our own rows plus reasonable interference.
    private static final int MAX_POLLS = 50;

    @Autowired
    private OutboxWorker outboxWorker;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private final List<UUID> savedEventIds = new ArrayList<>();

    @AfterEach
    void deleteTestEvents() {
        for (UUID id : savedEventIds) {
            outboxEventRepository.findById(id).ifPresent(outboxEventRepository::delete);
        }
        savedEventIds.clear();
    }

    @Test
    void processPendingEventsNeverExceedsConfiguredBatchSizeAndEventuallyDrainsAllSeededEvents() {
        Set<UUID> seededIds = seedPendingEvents(SEEDED_EVENT_COUNT);

        int polls = 0;
        while (!allProcessed(seededIds) && polls < MAX_POLLS) {
            List<OutboxEvent> batch = outboxWorker.processPendingEvents();

            // The invariant this test exists to prove — holds for every
            // single call, no matter whose rows ended up in it.
            assertThat(batch.size()).isLessThanOrEqualTo(BATCH_SIZE);
            assertThat(batch).allSatisfy(
                    event -> assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSED));

            polls++;
        }

        assertThat(polls)
                .withFailMessage("gave up after %d polls without draining all seeded events", MAX_POLLS)
                .isLessThan(MAX_POLLS);
        assertThat(allProcessed(seededIds)).isTrue();
    }

    private boolean allProcessed(Set<UUID> ids) {
        return outboxEventRepository.findAllById(ids).stream()
                .allMatch(event -> event.getStatus() == OutboxEventStatus.PROCESSED);
    }

    private Set<UUID> seedPendingEvents(int count) {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < count; i++) {
            UUID id = outboxEventRepository.saveAndFlush(
                    new OutboxEvent("BatchSizeTest", UUID.randomUUID(), "TestEvent", "{}")).getId();
            ids.add(id);
            savedEventIds.add(id);
        }
        return ids;
    }

}
