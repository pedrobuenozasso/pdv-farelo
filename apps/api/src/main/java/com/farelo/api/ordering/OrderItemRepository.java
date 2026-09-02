package com.farelo.api.ordering;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    // JOIN FETCH product, same reasoning as
    // OrderRepository#findByCommandOrderByCreatedAtAsc — OrderItemResponse
    // reads product.getName(), so an uninitialized lazy proxy here would
    // throw LazyInitializationException once the (short) fetching
    // transaction closes, before the web layer builds the response.
    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.product WHERE oi.order = :order")
    List<OrderItem> findByOrder(@Param("order") Order order);

}
