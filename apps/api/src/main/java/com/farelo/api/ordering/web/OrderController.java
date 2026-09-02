package com.farelo.api.ordering.web;

import com.farelo.api.ordering.NewOrderItem;
import com.farelo.api.ordering.OrderService;
import com.farelo.api.ordering.OrderWithItems;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

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

    // Kitchen queue (FARELO-059): every order across every command that
    // still needs kitchen attention (CREATED/CONFIRMED/PREPARING), oldest
    // first. Conceptually a `kitchen` domain concern (see docs/domain-model
    // .md), but it's a single read endpoint reusing OrderService/Order
    // entirely — putting it here reuses the existing `/api/v1/orders` root
    // resource controller instead of standing up a new `kitchen` package
    // for one endpoint (AGENTS.md: no abstração prematura). Revisit if/when
    // `kitchen` gains real responsibilities of its own (KDS printing, etc).
    @GetMapping
    public List<OrderResponse> queue() {
        return orderService.listQueue().stream()
                .map(OrderResponse::from)
                .toList();
    }

    // POST, not PATCH — same reasoning as
    // com.farelo.api.command.web.CommandController's /open, /close: this
    // is an action, not a partial representation update.
    @PostMapping("/{id}/preparing")
    public OrderResponse markAsPreparing(@PathVariable UUID id) {
        OrderWithItems result = orderService.markAsPreparing(id);
        return OrderResponse.from(result);
    }

    @PostMapping("/{id}/ready")
    public OrderResponse markAsReady(@PathVariable UUID id) {
        OrderWithItems result = orderService.markAsReady(id);
        return OrderResponse.from(result);
    }

    @PostMapping("/{id}/deliver")
    public OrderResponse markAsDelivered(@PathVariable UUID id) {
        OrderWithItems result = orderService.markAsDelivered(id);
        return OrderResponse.from(result);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse markAsCancelled(@PathVariable UUID id) {
        OrderWithItems result = orderService.markAsCancelled(id);
        return OrderResponse.from(result);
    }

}
