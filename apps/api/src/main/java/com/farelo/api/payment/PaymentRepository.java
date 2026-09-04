package com.farelo.api.payment;

import com.farelo.api.command.Command;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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

    // FARELO-142 ("Permitir múltiplos pagamentos por comanda"): backs
    // PaymentService#getTotalPaid / GET
    // /api/v1/commands/{number}/payments/total. Exact same shape as
    // InventoryMovementRepository#sumQuantityByIngredientId — a comanda's
    // total paid is defined as the sum of every Payment row recorded
    // against it (see Payment's javadoc: append-only ledger, so a plain SUM
    // is always correct, no status/void column to filter out).
    // COALESCE(..., 0) so a comanda with zero payments reports a total of
    // 0 instead of null, same reasoning as that precedent.
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.command = :command")
    BigDecimal sumAmountByCommand(@Param("command") Command command);

}
