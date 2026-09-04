package com.farelo.api.ordering;

import com.farelo.api.command.Command;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    // JOIN FETCH product, same reasoning as
    // OrderRepository#findByCommandOrderByCreatedAtAsc — OrderItemResponse
    // reads product.getName(), so an uninitialized lazy proxy here would
    // throw LazyInitializationException once the (short) fetching
    // transaction closes, before the web layer builds the response.
    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.product WHERE oi.order = :order")
    List<OrderItem> findByOrder(@Param("order") Order order);

    // Same JOIN FETCH reasoning as findByOrder above, scoped to a single
    // item by id — used by OrderService#cancelItem (FARELO-200/201), whose
    // return value is turned into an OrderItemResponse (product id/name)
    // after the @Transactional method (and its persistence context) has
    // already closed, so plain findById's lazy oi.product proxy would
    // throw LazyInitializationException on getName().
    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.product WHERE oi.id = :id")
    Optional<OrderItem> findByIdWithProduct(@Param("id") UUID id);

    // FARELO-143 ("Validar total pago antes de fechar"): backs
    // OrderService#getTotalOwed — a comanda's total bill, defined as the
    // sum of unitPrice * quantity across every OrderItem of every Order
    // belonging to it, EXCLUDING orders in :excludedStatus (CANCELLED, see
    // OrderService#getTotalOwed's javadoc for why). Same aggregate shape as
    // PaymentRepository#sumAmountByCommand/
    // InventoryMovementRepository#sumQuantityByIngredientId — COALESCE(...,
    // 0) so a comanda with no billable order items (no orders at all, or
    // every order cancelled) reports 0, not null. Traverses
    // oi.order.command rather than taking a pre-filtered list of orders:
    // OrderItem already has a path to Command through its required Order
    // association, so no extra join/parameter is needed beyond the two
    // this method already takes.
    //
    // FARELO-200/201: also excludes individually-cancelled items
    // (oi.cancelledAt IS NULL) — the ticket's own "recalcular valor da
    // comanda" requirement, satisfied here since this is the query
    // OrderService#getTotalOwed (and, via it, comanda-closing validation)
    // is built on. A cancelled item within an otherwise-live order must
    // not count toward what's owed.
    @Query("SELECT COALESCE(SUM(oi.unitPrice * oi.quantity), 0) FROM OrderItem oi "
            + "WHERE oi.order.command = :command AND oi.order.status <> :excludedStatus "
            + "AND oi.cancelledAt IS NULL")
    BigDecimal sumOwedByCommand(
            @Param("command") Command command, @Param("excludedStatus") OrderStatus excludedStatus);

}
