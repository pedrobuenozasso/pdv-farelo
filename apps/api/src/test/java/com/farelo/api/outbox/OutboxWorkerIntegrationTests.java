package com.farelo.api.outbox;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link OutboxWorker#processPendingEvents()} — the stub drain
 * loop (FARELO-060) — marks {@code PENDING} events {@code PROCESSED}.
 *
 * <p>Calls the {@code @Scheduled} method directly rather than waiting for
 * the real scheduler trigger — standard way to test scheduled methods
 * without depending on wall-clock timing.
 */
@SpringBootTest
class OutboxWorkerIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private OutboxWorker outboxWorker;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private UUID savedEventId;

    @AfterEach
    void deleteTestEvent() {
        if (savedEventId != null) {
            outboxEventRepository.deleteById(savedEventId);
        }
    }

    @Test
    void marksPendingEventAsProcessed() {
        OutboxEvent event = outboxEventRepository.saveAndFlush(
                new OutboxEvent("TestAggregate", UUID.randomUUID(), "TestEvent", "{}"));
        savedEventId = event.getId();

        outboxWorker.processPendingEvents();

        OutboxEvent reloaded = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxEventStatus.PROCESSED);
        assertThat(reloaded.getProcessedAt()).isNotNull();
    }

}
