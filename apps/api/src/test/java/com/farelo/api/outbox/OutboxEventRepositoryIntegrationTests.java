package com.farelo.api.outbox;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link OutboxEvent} maps correctly onto the table created
 * by {@code V10__create_outbox_event_table.sql}, against a real PostgreSQL
 * instance.
 */
@SpringBootTest
class OutboxEventRepositoryIntegrationTests extends AbstractIntegrationTest {

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
    void savesAndFindsPendingEventByStatus() {
        UUID aggregateId = UUID.randomUUID();
        OutboxEvent event = outboxEventRepository.saveAndFlush(
                new OutboxEvent("TestAggregate", aggregateId, "TestEvent", "{\"foo\":\"bar\"}"));
        savedEventId = event.getId();

        assertThat(event.getId()).isNotNull();
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getProcessedAt()).isNull();

        Optional<OutboxEvent> found = outboxEventRepository.findById(event.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getAggregateType()).isEqualTo("TestAggregate");
        assertThat(found.get().getAggregateId()).isEqualTo(aggregateId);
        assertThat(found.get().getEventType()).isEqualTo("TestEvent");
        // jsonb round-trip: written as a raw JSON string, read back as one.
        // Content-checked rather than exact-matched — Postgres re-serializes
        // jsonb text (e.g. spacing around ":"), so the exact bytes aren't
        // guaranteed to match what was written.
        assertThat(found.get().getPayload()).contains("\"foo\"").contains("\"bar\"");

        List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);
        assertThat(pending).extracting(OutboxEvent::getId).contains(event.getId());
    }

    @Test
    void markProcessedSetsStatusAndProcessedAt() {
        OutboxEvent event = outboxEventRepository.saveAndFlush(
                new OutboxEvent("TestAggregate", UUID.randomUUID(), "TestEvent", "{}"));
        savedEventId = event.getId();

        event.markProcessed();
        outboxEventRepository.saveAndFlush(event);

        OutboxEvent reloaded = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxEventStatus.PROCESSED);
        assertThat(reloaded.getProcessedAt()).isNotNull();
    }

}
