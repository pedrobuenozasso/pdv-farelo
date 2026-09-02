package com.farelo.api.ordering;

import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductNotAvailableException;
import com.farelo.api.catalog.ProductService;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CommandService commandService;
    private final ProductService productService;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            CommandService commandService,
            ProductService productService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.commandService = commandService;
        this.productService = productService;
    }

    /**
     * Creates an order with its items in a single transaction (prompt
     * mestre seção 30): validates the command can accept a new order
     * (transitioning {@code AVAILABLE} to {@code OPEN} if needed — see
     * {@link CommandService#openForOrdering(int)}), validates every
     * product exists and is active, and freezes each item's price at the
     * product's current price ({@code unitPrice} — FARELO-052's price
     * snapshot). No outbox/events yet (Epic 5, FARELO-060+).
     */
    @Transactional
    public OrderCreationResult create(int commandNumber, List<NewOrderItem> newItems) {
        Command command = commandService.openForOrdering(commandNumber);

        Order order = orderRepository.save(new Order(command));

        List<OrderItem> items = new ArrayList<>();
        for (NewOrderItem newItem : newItems) {
            Product product = productService.getById(newItem.productId());

            if (!product.isActive()) {
                throw new ProductNotAvailableException(product.getId());
            }

            // Price snapshot: unitPrice is frozen from product.getPrice()
            // at this exact moment — never a live reference to Product. If
            // the product's price changes later, this OrderItem keeps the
            // price captured here (AGENTS.md price-snapshot convention).
            OrderItem item = new OrderItem(order, product, newItem.quantity(), product.getPrice());
            items.add(orderItemRepository.save(item));
        }

        return new OrderCreationResult(order, items);
    }

}
