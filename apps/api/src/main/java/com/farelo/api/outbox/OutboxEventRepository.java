package com.farelo.api.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    // Backs OutboxWorker#processPendingEvents' polling loop — oldest
    // first, same FIFO reasoning as OrderRepository's queue query.
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status);

    // Backs OutboxWorker#processPendingEvents' polling loop (FARELO-063 —
    // see the class javadoc and docs/domain-model.md's "Outbox" section for
    // why this replaced findByStatusOrderByCreatedAtAsc(...) there
    // specifically). `FOR UPDATE SKIP LOCKED` is what makes concurrent
    // worker instances safe against double-processing the same row: each
    // caller's transaction row-locks whatever it selects, and any other
    // transaction running this same query concurrently silently skips rows
    // already locked elsewhere instead of blocking on them (or, without
    // SKIP LOCKED, both would happily select the very same PENDING rows and
    // both would go on to "process" them). `LIMIT :limit` bounds how many
    // rows a single call can lock, so one worker execution can never hold
    // an unbounded number of row locks — see OutboxWorker's `batchSize`.
    //
    // Native SQL, not JPQL: JPQL has no `SKIP LOCKED` keyword. Hibernate
    // does have an undocumented trick for this via
    // `@Lock(PESSIMISTIC_WRITE)` plus a `jakarta.persistence.lock.timeout`
    // query hint set to Hibernate's internal SKIP_LOCKED sentinel (-2), but
    // that leans on an internal implementation detail rather than a
    // documented, stable API. A native query that spells out exactly the
    // SQL Postgres runs is the more explicit, more portable-across-Hibernate
    // -versions way to express this — no less "real JPA" than a native
    // query is anywhere else, and the entity mapping still works
    // automatically here because the declared return type is the managed
    // entity and `SELECT *` yields column labels that already match
    // OutboxEvent's own @Column names.
    //
    // `status` is bound as a String (the enum's name()), not the enum
    // itself — native queries don't go through the entity's
    // @Enumerated(STRING) parameter conversion the way JPQL/derived queries
    // do, so the caller passes the already-stringified value explicitly.
    @Query(value = "SELECT * FROM outbox_event WHERE status = :status ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<OutboxEvent> findPendingForUpdateSkipLocked(@Param("status") String status, @Param("limit") int limit);

    // Backs OutboxMetrics' "outbox.events.pending" gauge (FARELO-062).
    // Standard Spring Data derived count query — cheap, and this is polled
    // on every metrics scrape.
    long countByStatus(OutboxEventStatus status);

    // Backs OutboxMetrics' "outbox.events.pending.oldest.age" gauge
    // (FARELO-062). Deliberately its own aggregate query rather than
    // reusing findByStatusOrderByCreatedAtAsc(...) and reading the first
    // element: that method loads every matching OutboxEvent (payload
    // included) just to discard all but one. A gauge is re-evaluated on
    // every metrics scrape, and the exact failure mode this metric exists
    // to catch is the PENDING queue growing unbounded — the one situation
    // where loading the whole list would be most expensive. A single
    // MIN(created_at) aggregate avoids that entirely.
    @Query("SELECT MIN(e.createdAt) FROM OutboxEvent e WHERE e.status = :status")
    Optional<OffsetDateTime> findOldestCreatedAtByStatus(@Param("status") OutboxEventStatus status);

    // Backs OutboxRetentionCleaner#deleteProcessedEventsOlderThanRetentionPeriod
    // (FARELO-061). A bulk JPQL DELETE via @Modifying, not a derived
    // deleteByStatusAndProcessedAtBefore(...) method — Spring Data's
    // derived delete methods load every matching entity and remove them
    // one at a time, which doesn't scale for a cleanup job whose entire
    // purpose is to reclaim rows that may have accumulated. A single bulk
    // DELETE statement avoids that per-row overhead.
    //
    // `status` is a parameter (rather than hardcoding PROCESSED in the
    // query) only so the "never touches PENDING" guarantee lives visibly
    // at the caller (OutboxRetentionCleaner), not implicitly inside this
    // query.
    //
    // clearAutomatically = true: a bulk JPQL DELETE like this bypasses the
    // persistence context — it issues SQL straight at the database without
    // touching Hibernate's first-level cache. Without clearing, any
    // OutboxEvent already managed in the same persistence context (e.g. an
    // entity a caller loaded/saved earlier in the same transaction) would
    // keep being returned by a later find() as if it still existed, even
    // though the row is already gone at the DB level. Clearing forces any
    // later read in the same transaction back to the database.
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM OutboxEvent e WHERE e.status = :status AND e.processedAt < :cutoff")
    int deleteByStatusAndProcessedAtBefore(@Param("status") OutboxEventStatus status, @Param("cutoff") OffsetDateTime cutoff);

}
