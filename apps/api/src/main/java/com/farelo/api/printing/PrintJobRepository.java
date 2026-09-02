package com.farelo.api.printing;

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

    // Backs GET /api/v1/print-jobs (FARELO-076) via PrintJobService#listPending
    // — oldest first, same FIFO reasoning as OrderRepository's kitchen-queue
    // query and OutboxEventRepository's polling query. JOIN FETCH p.order,
    // same reasoning as OrderRepository's queries: PrintJobResponse reads
    // job.getOrder().getId() in the controller, after this method's own
    // (short) transaction has already closed — without eagerly fetching the
    // association here, that would leave an uninitialized lazy proxy needing
    // a live session to resolve, risking LazyInitializationException (the
    // FARELO-055 lesson, documented on OrderRepository).
    @Query("SELECT p FROM PrintJob p JOIN FETCH p.order WHERE p.status = :status ORDER BY p.createdAt ASC")
    List<PrintJob> findByStatusOrderByCreatedAtAsc(@Param("status") PrintJobStatus status);

    // Backs PrintJobService#getById (FARELO-077, used by markPrinted/
    // markFailed) — same JOIN FETCH reasoning as findByStatusOrderByCreatedAtAsc
    // above and as OrderRepository#findByIdWithCommand: PrintJobResponse
    // reads job.getOrder().getId() in the controller, after this method's
    // own (short) transaction has already closed.
    @Query("SELECT p FROM PrintJob p JOIN FETCH p.order WHERE p.id = :id")
    Optional<PrintJob> findByIdWithOrder(@Param("id") UUID id);

}
