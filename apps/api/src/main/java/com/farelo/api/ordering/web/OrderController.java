package com.farelo.api.ordering.web;

import com.farelo.api.ordering.NewOrderItem;
import com.farelo.api.ordering.OrderService;
import com.farelo.api.ordering.OrderWithItems;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            UriComponentsBuilder uriComponentsBuilder) {
        List<NewOrderItem> items = request.items().stream()
                .map(item -> new NewOrderItem(item.productId(), item.quantity()))
                .toList();

        OrderWithItems result = orderService.create(request.commandNumber(), items);

        // No GET /api/v1/orders/{id} yet (future ticket) — the Location
        // header still names the resource's URI, which is correct even
        // before a handler exists to resolve it.
        URI location = uriComponentsBuilder
                .path("/api/v1/orders/{id}")
                .buildAndExpand(result.order().getId())
                .toUri();

        return ResponseEntity.created(location).body(OrderResponse.from(result));
    }

}
