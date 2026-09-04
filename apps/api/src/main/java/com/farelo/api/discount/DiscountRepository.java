package com.farelo.api.discount;

import com.farelo.api.command.Command;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface DiscountRepository extends JpaRepository<Discount, UUID> {

    // Backs GET /api/v1/commands/{number}/discounts (DiscountService#listByCommand)
    // — oldest first, same FIFO reasoning as PaymentRepository's equivalent
    // query. JOIN FETCH command: DiscountResponse reads
    // discount.getCommand().getNumber() in the controller, after this
    // method's own (short) transaction has already closed (open-in-view is
    // false) — a plain findByCommand(...) would leave `command` as an
    // uninitialized lazy proxy and throw LazyInitializationException.
    @Query("SELECT d FROM Discount d JOIN FETCH d.command WHERE d.command = :command ORDER BY d.createdAt ASC")
    List<Discount> findByCommandOrderByCreatedAtAsc(@Param("command") Command command);

    // Backs DiscountService#getTotalDiscount / PaymentService#getBalance
    // (FARELO-230/231, folded into FARELO-223's balance calculation) —
    // same COALESCE(..., 0) shape as PaymentRepository#sumAmountByCommand:
    // a comanda with zero discounts reports 0, not null. Sums
    // discountedAmount (the actual reduction), not originalAmount/percentage
    // — those are audit fields, not part of this aggregate.
    @Query("SELECT COALESCE(SUM(d.discountedAmount), 0) FROM Discount d WHERE d.command = :command")
    BigDecimal sumDiscountedAmountByCommand(@Param("command") Command command);

}
