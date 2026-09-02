package com.farelo.api.outbox;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link OutboxRetentionCleaner#deleteProcessedEventsOlderThanRetentionPeriod()}
 * (FARELO-061) end to end, against the real {@code outbox.retention.processed-days}
 * default configured in {@code application.yml} (7 days) — no property
 * override here, so this also doubles as a check that the config binds
 * correctly.
 *
 * <p>Calls the {@code @Scheduled} method directly rather than waiting for
 * the real hourly trigger — same approach as {@code
 * OutboxWorkerIntegrationTests}.
 */
@SpringBootTest
class OutboxRetentionCleanerIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private OutboxRetentionCleaner outboxRetentionCleaner;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

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
    void deletesOnlyProcessedEventsOlderThanTheConfiguredRetentionPeriod() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // PENDING, backdated 30 days — must survive regardless of age;
        // deleting a PENDING row would be real data loss (FARELO-061 scope).
        UUID pendingId = outboxEventRepository.saveAndFlush(
                new OutboxEvent("TestAggregate", UUID.randomUUID(), "TestEvent", "{}")).getId();
        savedEventIds.add(pendingId);
        backdateCreatedAt(pendingId, now.minusDays(30));

        // PROCESSED 1 day ago — younger than the 7-day default, must survive.
        OutboxEvent recentProcessed = outboxEventRepository.saveAndFlush(
                new OutboxEvent("TestAggregate", UUID.randomUUID(), "TestEvent", "{}"));
        recentProcessed.markProcessed();
        UUID recentProcessedId = outboxEventRepository.saveAndFlush(recentProcessed).getId();
        savedEventIds.add(recentProcessedId);
        backdateProcessedAt(recentProcessedId, now.minusDays(1));

        // PROCESSED 10 days ago — older than the 7-day default, must be deleted.
        OutboxEvent oldProcessed = outboxEventRepository.saveAndFlush(
                new OutboxEvent("TestAggregate", UUID.randomUUID(), "TestEvent", "{}"));
        oldProcessed.markProcessed();
        UUID oldProcessedId = outboxEventRepository.saveAndFlush(oldProcessed).getId();
        savedEventIds.add(oldProcessedId);
        backdateProcessedAt(oldProcessedId, now.minusDays(10));

        outboxRetentionCleaner.deleteProcessedEventsOlderThanRetentionPeriod();

        assertThat(outboxEventRepository.findById(pendingId)).isPresent();
        assertThat(outboxEventRepository.findById(recentProcessedId)).isPresent();
        assertThat(outboxEventRepository.findById(oldProcessedId)).isEmpty();
    }

    private void backdateProcessedAt(UUID id, OffsetDateTime processedAt) {
        jdbcTemplate.update(
                "UPDATE outbox_event SET processed_at = ? WHERE id = ?",
                Timestamp.from(processedAt.toInstant()), id);
    }

    private void backdateCreatedAt(UUID id, OffsetDateTime createdAt) {
        jdbcTemplate.update(
                "UPDATE outbox_event SET created_at = ? WHERE id = ?",
                Timestamp.from(createdAt.toInstant()), id);
    }

}
