package com.farelo.api.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Periodically deletes {@code PROCESSED} {@link OutboxEvent} rows older
 * than a configurable retention period (FARELO-061) — the operational
 * counterpart to {@link OutboxWorker}: the worker marks rows {@code
 * PROCESSED} but never removes them, so without this class {@code
 * outbox_event} would grow forever, one row per event ever published, even
 * though a processed row has no further job to do once drained.
 *
 * <p><strong>Separate class from {@link OutboxWorker}, on purpose</strong>
 * (the ticket left this as an implementation choice — this is the
 * reasoning): the worker's job is "notice new work and do it" (poll {@code
 * PENDING}, dispatch); this class's job is unrelated janitorial cleanup of
 * rows the worker has already finished with. Different responsibility,
 * different natural cadence (this runs hourly — retention correctness
 * doesn't depend on running anywhere near as often as the worker's 5s
 * poll, which exists to keep dispatch latency low once a real consumer
 * exists), and no shared state or control flow with the worker. A second
 * {@code @Scheduled} method bolted onto {@link OutboxWorker} would still
 * work, but would make that class do two unrelated things for no real
 * benefit — splitting costs one small extra class in exchange for each
 * class staying about one job.
 *
 * <p><strong>Only {@code PROCESSED} rows are ever deleted, regardless of
 * age.</strong> A {@code PENDING} row is exactly the data the outbox
 * exists to protect until a consumer has handled it — deleting one, no
 * matter how old, would be silent data loss for whatever event it
 * represents. {@link OutboxEventRepository#deleteByStatusAndProcessedAtBefore}
 * is always called here with {@link OutboxEventStatus#PROCESSED}, never
 * with an unfiltered "older than X" condition.
 *
 * <p><strong>Retention period</strong>: {@code
 * outbox.retention.processed-days}, default 7 days (see {@code
 * application.yml}) — long enough that a processed event is still around
 * for a day or more of operational troubleshooting (e.g. "did this order's
 * event actually get drained, and when?"), short enough that the table
 * doesn't keep an effectively unbounded history of rows nothing reads
 * anymore. No consumer or reporting need has ever depended on outbox
 * history past that window — if one appears, it should read from a
 * purpose-built place (e.g. {@code audit}, a future domain already listed
 * in {@code docs/domain-model.md}), not rely on this table being a
 * long-term log.
 */
@Component
public class OutboxRetentionCleaner {

    private static final Logger log = LoggerFactory.getLogger(OutboxRetentionCleaner.class);

    private final OutboxEventRepository outboxEventRepository;
    private final int retentionDays;

    public OutboxRetentionCleaner(
            OutboxEventRepository outboxEventRepository,
            @Value("${outbox.retention.processed-days:7}") int retentionDays) {
        this.outboxEventRepository = outboxEventRepository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void deleteProcessedEventsOlderThanRetentionPeriod() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(retentionDays);
        int deleted = outboxEventRepository.deleteByStatusAndProcessedAtBefore(OutboxEventStatus.PROCESSED, cutoff);

        if (deleted > 0) {
            log.info("Outbox retention cleanup deleted {} PROCESSED event(s) older than {} day(s) (processedAt < {})",
                    deleted, retentionDays, cutoff);
        }
    }

}
