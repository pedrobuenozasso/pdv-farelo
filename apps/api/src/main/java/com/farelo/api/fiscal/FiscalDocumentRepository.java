package com.farelo.api.fiscal;

import com.farelo.api.command.Command;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

}
