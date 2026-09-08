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
    // boundaries. Scoped by :command, so every row here is COMANDA-typed
    // (a BALCAO order has no command to match) — a plain JOIN (not LEFT
    // JOIN) is correct here, unlike the two queries below.
    @Query("SELECT o FROM Order o JOIN FETCH o.command WHERE o.command = :command ORDER BY o.createdAt ASC")
    List<Order> findByCommandOrderByCreatedAtAsc(@Param("command") Command command);

    // LEFT JOIN FETCH (FARELO-312): a plain JOIN FETCH would silently
    // exclude a BALCAO order (whose command is null) from the result
    // entirely — an inner join drops any row with a null command_id. This
    // single-id lookup must still find a BALCAO order (used by
    // OrderService#getById, in turn used by every status-transition
    // method — a BALCAO order needs the exact same PREPARING/READY/
    // DELIVERED/CANCELLED lifecycle as a COMANDA one), so the join must be
    // outer, not inner. `command` is still eagerly fetched either way — a
    // COMANDA order's association is populated, a BALCAO order's is simply
    // null, never an uninitialized proxy.
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.command WHERE o.id = :id")
    Optional<Order> findByIdWithCommand(@Param("id") UUID id);

    // Same LEFT JOIN FETCH reasoning as above, for the kitchen queue
    // (FARELO-059, OrderService#listQueue): across every command, not
    // scoped to one — a BALCAO order needs kitchen attention exactly like
    // a COMANDA one (its items still need preparing), so this must not
    // silently drop it the way an inner JOIN FETCH would (see
    // findByIdWithCommand's comment above).
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.command WHERE o.status IN :statuses ORDER BY o.createdAt ASC")
    List<Order> findByStatusInOrderByCreatedAtAsc(@Param("statuses") Collection<OrderStatus> statuses);

}
