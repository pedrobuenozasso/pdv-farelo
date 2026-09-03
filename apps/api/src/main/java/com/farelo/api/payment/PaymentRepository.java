package com.farelo.api.payment;

import com.farelo.api.command.Command;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    // Backs GET /api/v1/commands/{number}/payments (PaymentService#listByCommand)
    // — oldest first, same ordering direction/convention as
    // OrderRepository#findByCommandOrderByCreatedAtAsc. JOIN FETCH command
    // for the exact same reason documented on that query: PaymentResponse
    // reads payment.getCommand().getNumber() in the controller, after this
    // method's (short) transaction has already closed (open-in-view is
    // false, application.yml) — a plain findByCommand(...) would leave
    // `command` as an uninitialized lazy proxy and throw
    // LazyInitializationException the moment that getter is called.
    @Query("SELECT p FROM Payment p JOIN FETCH p.command WHERE p.command = :command ORDER BY p.createdAt ASC")
    List<Payment> findByCommandOrderByCreatedAtAsc(@Param("command") Command command);

}
