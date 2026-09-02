package com.farelo.api.outbox;

import com.farelo.api.AbstractIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link OutboxMetrics} (FARELO-062) registers both gauges on the
 * real {@link MeterRegistry} with values that reflect {@code outbox_event},
 * and that they're reachable at {@code /actuator/metrics} (the reason
 * {@code metrics} was added to {@code management.endpoints.web.exposure.
 * include} in {@code application.yml} — previously only {@code health} was
 * opted in).
 *
 * <p>Delta/lower-bound assertions throughout, not exact-count ones:
 * {@code outbox_event} is shared across every test class against the
 * singleton Postgres container ({@code AbstractIntegrationTest}), same
 * reasoning as {@code OutboxPublisherIntegrationTests}' cleanup — another
 * test class's rows may be {@code PENDING} at the same instant this test
 * runs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OutboxMetricsIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    private final List<UUID> savedEventIds = new ArrayList<>();

    @AfterEach
    void deleteTestEvents() {
        for (UUID id : savedEventIds) {
            outboxEventRepository.findById(id).ifPresent(outboxEventRepository::delete);
        }
        savedEventIds.clear();
    }

    @Test
    void pendingGaugeReflectsNumberOfPendingEventsCreated() {
        double before = meterRegistry.get("outbox.events.pending").gauge().value();

        for (int i = 0; i < 3; i++) {
            UUID id = outboxEventRepository.saveAndFlush(
                    new OutboxEvent("TestAggregate", UUID.randomUUID(), "TestEvent", "{}")).getId();
            savedEventIds.add(id);
        }

        double after = meterRegistry.get("outbox.events.pending").gauge().value();
        assertThat(after).isEqualTo(before + 3);
    }

    @Test
    void oldestPendingAgeGaugeReflectsAgeOfOldestPendingEvent() {
        UUID id = outboxEventRepository.saveAndFlush(
                new OutboxEvent("TestAggregate", UUID.randomUUID(), "TestEvent", "{}")).getId();
        savedEventIds.add(id);

        OffsetDateTime backdatedCreatedAt = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(120);
        jdbcTemplate.update("UPDATE outbox_event SET created_at = ? WHERE id = ?",
                Timestamp.from(backdatedCreatedAt.toInstant()), id);

        double ageSeconds = meterRegistry.get("outbox.events.pending.oldest.age").gauge().value();

        // MIN(created_at) across every PENDING row can only be at or before
        // our backdated event's created_at, so the age can only be >= what
        // we backdated to — holds even if other tests' PENDING rows exist
        // concurrently in the shared table.
        assertThat(ageSeconds).isGreaterThanOrEqualTo(100.0);
    }

    @Test
    void gaugesAreReachableViaActuatorMetricsEndpoint() {
        TestRestTemplate restTemplate = new TestRestTemplate();

        ResponseEntity<String> pendingResponse = restTemplate.getForEntity(
                "http://localhost:%d/actuator/metrics/outbox.events.pending".formatted(port), String.class);
        assertThat(pendingResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(pendingResponse.getBody()).contains("\"outbox.events.pending\"");

        ResponseEntity<String> oldestAgeResponse = restTemplate.getForEntity(
                "http://localhost:%d/actuator/metrics/outbox.events.pending.oldest.age".formatted(port), String.class);
        assertThat(oldestAgeResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(oldestAgeResponse.getBody()).contains("\"outbox.events.pending.oldest.age\"");
    }

}
