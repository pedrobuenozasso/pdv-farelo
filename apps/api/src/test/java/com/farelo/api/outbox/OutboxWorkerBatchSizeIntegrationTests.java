package com.farelo.api.outbox;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
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
 * <p>{@code outbox.worker.poll-interval-ms} is also pushed out to an hour
 * here — this test calls {@link OutboxWorker#processPendingEvents()}
 * directly and needs an *exact* count of what that one call processed. With
 * the real {@code @Scheduled} trigger still running on its normal interval
 * in the background (this is a full {@code @SpringBootTest} context, not a
 * slice), it could fire between this test seeding its fixture and making
 * its own explicit call, draining some of the seeded rows first and
 * throwing off the exact-count assertions below — a real failure this test
 * caught once. Pushing the interval out makes that race impossible instead
 * of just unlikely.
 *
 * <p>{@link #drainAnyPreexistingPendingEvents()} clears the table of any
 * {@code PENDING} rows left behind by another test class before seeding
 * this test's own fixture — {@code outbox_event} is shared across the
 * singleton Postgres container, and this test needs an exact count (not
 * just a lower bound) to assert the batch size precisely.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "outbox.worker.batch-size=3",
        "outbox.worker.poll-interval-ms=3600000"
})
class OutboxWorkerBatchSizeIntegrationTests extends AbstractIntegrationTest {

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
    void processPendingEventsNeverExceedsConfiguredBatchSize() {
        drainAnyPreexistingPendingEvents();

        for (int i = 0; i < 5; i++) {
            UUID id = outboxEventRepository.saveAndFlush(
                    new OutboxEvent("BatchSizeTest", UUID.randomUUID(), "TestEvent", "{}")).getId();
            savedEventIds.add(id);
        }

        List<OutboxEvent> firstBatch = outboxWorker.processPendingEvents();
        assertThat(firstBatch).hasSize(3);
        assertThat(firstBatch).allSatisfy(
                event -> assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSED));
        assertThat(outboxEventRepository.countByStatus(OutboxEventStatus.PENDING)).isEqualTo(2);

        // A second call drains the remainder — confirms batch-size is a
        // per-call cap, not a hard ceiling on total throughput.
        List<OutboxEvent> secondBatch = outboxWorker.processPendingEvents();
        assertThat(secondBatch).hasSize(2);
        assertThat(outboxEventRepository.countByStatus(OutboxEventStatus.PENDING)).isEqualTo(0);
    }

    private void drainAnyPreexistingPendingEvents() {
        List<OutboxEvent> drained;
        do {
            drained = outboxWorker.processPendingEvents();
        } while (!drained.isEmpty());
    }

}
