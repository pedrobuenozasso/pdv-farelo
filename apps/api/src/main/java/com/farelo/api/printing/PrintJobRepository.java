package com.farelo.api.printing;

import com.farelo.api.command.Command;
import com.farelo.api.ordering.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrintJobRepository extends JpaRepository<PrintJob, UUID> {

    // Derived query (Spring Data), same minimal style as the rest of this
    // repository. Added for FARELO-072: lets a caller (today, only tests)
    // confirm which PrintJob(s) exist for a given order, without needing a
    // new custom @Query — order is @ManyToOne on PrintJob, so a plain
    // equality WHERE clause is all this needs.
    List<PrintJob> findByOrder(Order order);

    // FARELO-210/211 counterpart to findByOrder above, same derived-query
    // style — lets a caller (today, only tests cleaning up a COMMAND_CHECK
    // job by command, without risking a LazyInitializationException from
    // reading job.getCommand().getNumber() off an unfetched proxy) find
    // every PrintJob for a given command.
    List<PrintJob> findByCommand(Command command);

    // Backs GET /api/v1/print-jobs (FARELO-076) via PrintJobService#listPending
    // — oldest first, same FIFO reasoning as OrderRepository's kitchen-queue
    // query and OutboxEventRepository's polling query. LEFT JOIN FETCH (not
    // plain JOIN FETCH, since FARELO-210/211): p.order and p.command are now
    // mutually exclusive-nullable (see PrintJob's javadoc, "Design decision
    // 5") — an inner JOIN FETCH on either would silently drop every row of
    // the other type from this result, which would mean COMMAND_CHECK jobs
    // never reach the Edge Agent's poll at all. PrintJobResponse reads
    // job.getOrder().getId()/job.getCommand().getNumber() in the controller,
    // after this method's own (short) transaction has already closed, so
    // whichever association is populated must still be eagerly fetched here
    // (the FARELO-055 lesson, documented on OrderRepository) — LEFT JOIN
    // FETCH does that for both without requiring either to be non-null.
    @Query("SELECT p FROM PrintJob p LEFT JOIN FETCH p.order LEFT JOIN FETCH p.command "
            + "WHERE p.status = :status ORDER BY p.createdAt ASC")
    List<PrintJob> findByStatusOrderByCreatedAtAsc(@Param("status") PrintJobStatus status);

    // Backs PrintJobService#getById (FARELO-077, used by markPrinted/
    // markFailed/retry) — same LEFT JOIN FETCH reasoning as
    // findByStatusOrderByCreatedAtAsc above. Renamed from findByIdWithOrder
    // (FARELO-210/211): it now fetches both possible associations, not just
    // order.
    @Query("SELECT p FROM PrintJob p LEFT JOIN FETCH p.order LEFT JOIN FETCH p.command WHERE p.id = :id")
    Optional<PrintJob> findByIdWithAssociations(@Param("id") UUID id);

}
