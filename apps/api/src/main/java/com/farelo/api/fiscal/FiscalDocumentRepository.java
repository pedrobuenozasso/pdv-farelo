package com.farelo.api.fiscal;

import com.farelo.api.command.Command;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FiscalDocumentRepository extends JpaRepository<FiscalDocument, UUID> {

    // Backs GET /api/v1/commands/{number}/fiscal-documents
    // (FiscalDocumentService#listByCommand) — oldest first, same ordering
    // direction/convention as
    // PaymentRepository#findByCommandOrderByCreatedAtAsc/
    // OrderRepository#findByCommandOrderByCreatedAtAsc. JOIN FETCH command
    // for the exact same reason documented on those queries: open-in-view is
    // false (application.yml), and FiscalDocumentResponse#from reads
    // fiscalDocument.getCommand().getNumber() in the controller, after this
    // method's (short) transaction has already closed — a plain
    // findByCommand(...) would leave `command` as an uninitialized lazy
    // proxy and throw LazyInitializationException the moment that getter is
    // called.
    @Query("SELECT fd FROM FiscalDocument fd JOIN FETCH fd.command WHERE fd.command = :command ORDER BY fd.createdAt ASC")
    List<FiscalDocument> findByCommandOrderByCreatedAtAsc(@Param("command") Command command);

    // FARELO-157: backs FiscalDocumentService#getById/transition. Same JOIN
    // FETCH reasoning as above and as
    // com.farelo.api.ordering.OrderRepository#findByIdWithCommand/
    // com.farelo.api.printing.PrintJobRepository#findByIdWithOrder — a plain
    // findById(id) would leave `command` as an uninitialized lazy proxy, and
    // both FiscalDocumentResponse#from and the command-number ownership
    // check in FiscalDocumentService#transition(int, UUID,
    // FiscalDocumentStatus) need command.getId()/getNumber() after this
    // method's own (short) transaction has already closed.
    @Query("SELECT fd FROM FiscalDocument fd JOIN FETCH fd.command WHERE fd.id = :id")
    Optional<FiscalDocument> findByIdWithCommand(@Param("id") UUID id);

}
