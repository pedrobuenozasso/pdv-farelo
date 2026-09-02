package com.farelo.api.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Exposes the health of the {@code outbox_event} queue as Micrometer gauges
 * (FARELO-062) — closes an operational gap left open since {@link
 * OutboxWorker} (FARELO-060): if the worker stops running (crash, bad
 * deploy, etc), {@code PENDING} events simply pile up with no external
 * signal that anything is wrong. This class doesn't fix that by itself —
 * it makes the queue's state observable from {@code /actuator/metrics} so
 * something outside the process (a scrape, an alert) can.
 *
 * <p>Two gauges, both scoped to {@link OutboxEventStatus#PENDING}:
 *
 * <ul>
 *   <li>{@code outbox.events.pending} — how many rows are waiting. Useful,
 *   but a healthy queue can legitimately sit above zero for a few seconds
 *   between {@link OutboxWorker} polls, so a raw count alone is a noisy
 *   signal to alert on.
 *   <li>{@code outbox.events.pending.oldest.age} (seconds) — how long the
 *   single oldest {@code PENDING} row has been waiting. This is the more
 *   direct signal of "the worker stopped draining": a healthy worker keeps
 *   this near its 5s poll interval regardless of queue size, while a dead
 *   worker lets it climb without bound. Reports {@code 0} when the queue is
 *   empty (no "oldest" event to report an age for) rather than a negative
 *   or missing value, so the gauge always has a well-defined reading.
 * </ul>
 *
 * <p>{@code Gauge} (not a {@code Counter}) — both values go up and down
 * with queue depth, never a cumulative total, which is exactly what a
 * Micrometer gauge (backed by a live callback, re-evaluated on every
 * scrape) is for.
 *
 * <p>Both gauges are registered against {@code this}/the repository as
 * their weakly-referenced state object (Micrometer's usual gauge pattern —
 * see {@link Gauge.Builder}); that's safe here because both are singleton
 * Spring beans kept strongly reachable by the application context for the
 * process lifetime, so they're never garbage-collected out from under the
 * registry.
 */
@Component
public class OutboxMetrics {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxMetrics(OutboxEventRepository outboxEventRepository, MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;

        Gauge.builder("outbox.events.pending", outboxEventRepository,
                        repository -> repository.countByStatus(OutboxEventStatus.PENDING))
                .description("Number of OutboxEvent rows currently PENDING (not yet drained by OutboxWorker).")
                .register(meterRegistry);

        Gauge.builder("outbox.events.pending.oldest.age", this, OutboxMetrics::oldestPendingAgeSeconds)
                .description("Age in seconds of the oldest PENDING OutboxEvent row — 0 when the queue is empty. "
                        + "The clearest signal that OutboxWorker has stopped draining the queue: a healthy worker "
                        + "keeps this near its poll interval regardless of queue depth.")
                .baseUnit("seconds")
                .register(meterRegistry);
    }

    private double oldestPendingAgeSeconds() {
        return outboxEventRepository.findOldestCreatedAtByStatus(OutboxEventStatus.PENDING)
                .map(oldestCreatedAt -> Duration.between(oldestCreatedAt, OffsetDateTime.now(ZoneOffset.UTC)).getSeconds())
                .map(Long::doubleValue)
                .orElse(0.0);
    }

}
