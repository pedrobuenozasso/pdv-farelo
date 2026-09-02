package com.farelo.api.outbox;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@link OutboxPublisher}'s two core guarantees: it persists a
 * {@code PENDING} {@link OutboxEvent}, and — the point of the whole
 * pattern — it participates in the caller's transaction rather than its
 * own, so a rollback of the enclosing business transaction takes the
 * outbox row down with it (atomicity).
 */
@SpringBootTest
class OutboxPublisherIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    private UUID aggregateId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        aggregateId = UUID.randomUUID();
    }

    @AfterEach
    void deleteTestEvents() {
        List<OutboxEvent> leftover = outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);
        leftover.stream()
                .filter(event -> event.getAggregateId().equals(aggregateId))
                .forEach(outboxEventRepository::delete);
    }

    @Test
    void publishPersistsPendingEventWhenEnclosingTransactionCommits() {
        transactionTemplate.execute(status -> {
            outboxPublisher.publish("TestAggregate", aggregateId, "TestEvent", Map.of("foo", "bar"));
            return null;
        });

        List<OutboxEvent> events = outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);
        OutboxEvent event = events.stream()
                .filter(e -> e.getAggregateId().equals(aggregateId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected event was not persisted"));

        assertThat(event.getAggregateType()).isEqualTo("TestAggregate");
        assertThat(event.getEventType()).isEqualTo("TestEvent");
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getPayload()).contains("\"foo\"").contains("\"bar\"");
    }

    // The core atomicity guarantee: publish() writes inside the caller's
    // transaction, not its own — so when that transaction later fails and
    // rolls back (simulating a business rule failing after the event was
    // published, e.g. OrderService#create failing on a later item), the
    // outbox row must never have been committed either. Either both the
    // domain write and the event commit, or neither does.
    @Test
    void publishRollsBackWithEnclosingTransactionOnFailure() {
        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            outboxPublisher.publish("TestAggregate", aggregateId, "TestEvent", Map.of("foo", "bar"));
            throw new RuntimeException("simulated business failure after publish");
        })).isInstanceOf(RuntimeException.class).hasMessage("simulated business failure after publish");

        boolean persisted = outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING).stream()
                .anyMatch(event -> event.getAggregateId().equals(aggregateId));
        assertThat(persisted).isFalse();
    }

    // publish() uses Propagation.MANDATORY deliberately (see its javadoc):
    // calling it with no transaction active must fail fast, rather than
    // silently persisting a row with no atomicity guarantee at all.
    @Test
    void publishRequiresAnActiveTransaction() {
        assertThatThrownBy(() -> outboxPublisher.publish("TestAggregate", aggregateId, "TestEvent", Map.of()))
                .isInstanceOf(IllegalTransactionStateException.class);

        boolean persisted = outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING).stream()
                .anyMatch(event -> event.getAggregateId().equals(aggregateId));
        assertThat(persisted).isFalse();
    }

}
