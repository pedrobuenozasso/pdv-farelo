package com.farelo.api.outbox;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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

    // JdbcTemplate, not the JPA repository: FARELO-061's retention test
    // needs a PROCESSED row whose processed_at is genuinely days old, and
    // OutboxEvent#markProcessed() only ever sets it to "now" — there's no
    // production code path that backdates it (nor should there be). A
    // direct SQL UPDATE is the simplest way to get that fixture state
    // without adding a test-only setter to the entity.
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> savedEventIds = new ArrayList<>();

    @AfterEach
    void deleteTestEvents() {
        for (UUID id : savedEventIds) {
            outboxEventRepository.findById(id).ifPresent(outboxEventRepository::delete);
        }
        savedEventIds.clear();
    }

    @Test
    void savesAndFindsPendingEventByStatus() {
        UUID aggregateId = UUID.randomUUID();
        OutboxEvent event = outboxEventRepository.saveAndFlush(
                new OutboxEvent("TestAggregate", aggregateId, "TestEvent", "{\"foo\":\"bar\"}"));
        savedEventIds.add(event.getId());

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
        savedEventIds.add(event.getId());

        event.markProcessed();
        outboxEventRepository.saveAndFlush(event);

        OutboxEvent reloaded = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxEventStatus.PROCESSED);
        assertThat(reloaded.getProcessedAt()).isNotNull();
    }

    // FARELO-061: OutboxRetentionCleaner's repository-level contract —
    // covered here (repository behavior) rather than only via
    // OutboxRetentionCleanerIntegrationTests (the scheduled mechanism),
    // per the ticket's explicit request for both. Uses findById presence/
    // absence rather than the returned delete count, since outbox_event is
    // shared across every test class against the singleton Postgres
    // container (AbstractIntegrationTest) — asserting on specific rows'
    // survival is robust to whatever else is in the table.
    //
    // @Transactional: unlike the other tests here, this one calls a
    // @Modifying bulk-DELETE query directly. Spring Data JPA's @Modifying
    // queries don't get an implicit transaction from the repository proxy
    // the way derived read/save methods do — the caller has to provide
    // one, same as OutboxPublisher#publish documents for its own
    // MANDATORY propagation. In production that caller is
    // OutboxRetentionCleaner (already @Transactional); here it's this
    // test method. Spring's test support rolls this transaction back
    // automatically at the end, so the manual @AfterEach cleanup below is
    // a harmless no-op for this test's own rows specifically.
    @Test
    @Transactional
    void deleteByStatusAndProcessedAtBeforeDeletesOnlyProcessedEventsOlderThanCutoff() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime cutoff = now.minusDays(7);

        // Never processed — must survive no matter how the cutoff is set;
        // a PENDING row's processed_at is NULL, which never satisfies
        // "< cutoff" in SQL, but this is exactly the case the ticket calls
        // out as never-delete-worthy, so it's asserted explicitly rather
        // than relying on that NULL-comparison behavior alone.
        UUID pendingId = outboxEventRepository.saveAndFlush(
                new OutboxEvent("TestAggregate", UUID.randomUUID(), "TestEvent", "{}")).getId();
        savedEventIds.add(pendingId);

        // Processed recently (processedAt ~= now) — younger than the
        // 7-day cutoff, must survive.
        OutboxEvent recentProcessed = outboxEventRepository.saveAndFlush(
                new OutboxEvent("TestAggregate", UUID.randomUUID(), "TestEvent", "{}"));
        recentProcessed.markProcessed();
        UUID recentProcessedId = outboxEventRepository.saveAndFlush(recentProcessed).getId();
        savedEventIds.add(recentProcessedId);

        // Processed, but backdated to 10 days ago — older than the 7-day
        // cutoff, must be deleted.
        OutboxEvent oldProcessed = outboxEventRepository.saveAndFlush(
                new OutboxEvent("TestAggregate", UUID.randomUUID(), "TestEvent", "{}"));
        oldProcessed.markProcessed();
        UUID oldProcessedId = outboxEventRepository.saveAndFlush(oldProcessed).getId();
        savedEventIds.add(oldProcessedId);
        backdateProcessedAt(oldProcessedId, now.minusDays(10));

        int deleted = outboxEventRepository.deleteByStatusAndProcessedAtBefore(OutboxEventStatus.PROCESSED, cutoff);

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(outboxEventRepository.findById(pendingId)).isPresent();
        assertThat(outboxEventRepository.findById(recentProcessedId)).isPresent();
        assertThat(outboxEventRepository.findById(oldProcessedId)).isEmpty();
    }

    private void backdateProcessedAt(UUID id, OffsetDateTime processedAt) {
        jdbcTemplate.update(
                "UPDATE outbox_event SET processed_at = ? WHERE id = ?",
                Timestamp.from(processedAt.toInstant()), id);
    }

}
