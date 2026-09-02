package com.farelo.api.ordering;

import com.farelo.api.command.Command;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    // JOIN FETCH command: unlike create() — where Order is built with an
    // already-loaded Command in memory — these Order rows come straight
    // from a query, so a plain findByCommand(...) would leave `command` as
    // an uninitialized lazy proxy. Since the response is built in the
    // controller, after this method's (short, per-call) transaction has
    // already closed, that proxy would throw LazyInitializationException
    // the moment OrderResponse.from() calls order.getCommand().getNumber().
    // Fetching it eagerly here avoids that regardless of transaction
    // boundaries.
    @Query("SELECT o FROM Order o JOIN FETCH o.command WHERE o.command = :command ORDER BY o.createdAt ASC")
    List<Order> findByCommandOrderByCreatedAtAsc(@Param("command") Command command);

    // Same JOIN FETCH reasoning as above, for single-order lookups (e.g.
    // OrderService#getById, used by the FARELO-057/058 status
    // transitions) — plain findById(id) would leave `command` as an
    // uninitialized proxy.
    @Query("SELECT o FROM Order o JOIN FETCH o.command WHERE o.id = :id")
    Optional<Order> findByIdWithCommand(@Param("id") UUID id);

    // Same JOIN FETCH reasoning as above, for the kitchen queue
    // (FARELO-059, OrderService#listQueue): across every command, not
    // scoped to one, so a plain "in" query without the fetch would still
    // leave `command` as an uninitialized proxy once OrderResponse.from()
    // reads order.getCommand().getNumber() after the transaction closes.
    @Query("SELECT o FROM Order o JOIN FETCH o.command WHERE o.status IN :statuses ORDER BY o.createdAt ASC")
    List<Order> findByStatusInOrderByCreatedAtAsc(@Param("statuses") Collection<OrderStatus> statuses);

}
