package com.farelo.api.ordering.web;

import com.farelo.api.ordering.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Exposes {@code GET /api/v1/commands/{number}/orders} (FARELO-055).
 *
 * <p>Placement note: this is a command-scoped URL, but lives in the
 * {@code ordering} domain ({@code com.farelo.api.ordering.web}) rather
 * than in {@code com.farelo.api.command.web.CommandController}. The
 * {@code ordering} domain already depends on {@code command} (
 * {@code Order.command}, {@code OrderService} calls {@code CommandService})
 * — keeping this controller here too means that dependency stays
 * one-directional. Putting it in {@code CommandController} would make
 * {@code command.web} depend on {@code ordering.OrderService}, a
 * cross-domain dependency in the opposite direction (AGENTS.md: "evitar
 * dependências cruzadas desnecessárias"). The URL path is independent of
 * which class handles it, so this costs nothing.
 */
@RestController
@RequestMapping("/api/v1/commands")
public class CommandOrdersController {

    private final OrderService orderService;

    public CommandOrdersController(OrderService orderService) {
        this.orderService = orderService;
    }

    // {number} is the command's business identifier, same convention as
    // com.farelo.api.command.web.CommandController.
    @GetMapping("/{number}/orders")
    public List<OrderResponse> listByCommand(@PathVariable int number) {
        return orderService.listByCommand(number).stream()
                .map(OrderResponse::from)
                .toList();
    }

}
