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
import java.util.UUID;

@Service
public class OrderService {

    // Statuses that still need kitchen attention, for the kitchen queue
    // (FARELO-059, listQueue below): everything before READY. CONFIRMED
    // has no transition into or out of it yet on the roadmap (see
    // OrderStatus/markAsPreparing's javadoc), but is included here since
    // conceptually it's still "not yet in the kitchen's hands", same as
    // CREATED.
    private static final List<OrderStatus> QUEUE_STATUSES =
            List.of(OrderStatus.CREATED, OrderStatus.CONFIRMED, OrderStatus.PREPARING);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final CommandService commandService;
    private final ProductService productService;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            CommandService commandService,
            ProductService productService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
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
     * snapshot). Also writes the order's first {@link OrderStatusHistory}
     * entry ({@code fromStatus = null}, {@code toStatus = CREATED}) —
     * FARELO-056; future status transitions (FARELO-057/058) append their
     * own entries the same way. No outbox/events yet (Epic 5, FARELO-060+).
     */
    @Transactional
    public OrderWithItems create(int commandNumber, List<NewOrderItem> newItems) {
        Command command = commandService.openForOrdering(commandNumber);

        Order order = orderRepository.save(new Order(command));
        orderStatusHistoryRepository.save(new OrderStatusHistory(order, null, OrderStatus.CREATED));

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

        return new OrderWithItems(order, items);
    }

    /**
     * Lists every order placed on a command, oldest first, each with its
     * items (FARELO-055). No pagination — same YAGNI reasoning already
     * applied to {@code GET /api/v1/categories}/{@code /products}: the
     * number of orders on a single command is naturally small.
     *
     * <p>One query per order to fetch its items (N+1) rather than a bulk
     * fetch — deliberately simple, consistent with the same "naturally
     * small" reasoning above; revisit if a command ever accumulates enough
     * orders for this to matter.
     *
     * <p>{@code @Transactional(readOnly = true)}: without it, each
     * repository call below runs in its own short transaction, and the
     * response is built later in the controller — after every one of them
     * has already closed. The {@code JOIN FETCH} in
     * {@code OrderRepository}/{@code OrderItemRepository} is what actually
     * prevents {@code LazyInitializationException} there (association
     * proxies would otherwise need a live session to resolve); this
     * annotation is about treating the multi-query read as one logical
     * unit, not a substitute for that fetch strategy.
     */
    @Transactional(readOnly = true)
    public List<OrderWithItems> listByCommand(int commandNumber) {
        Command command = commandService.findByNumber(commandNumber);
        List<Order> orders = orderRepository.findByCommandOrderByCreatedAtAsc(command);

        return orders.stream()
                .map(order -> new OrderWithItems(order, orderItemRepository.findByOrder(order)))
                .toList();
    }

    /**
     * Lists every order, across every command, that still needs kitchen
     * attention — status {@code CREATED}, {@code CONFIRMED} or
     * {@code PREPARING} (see {@link #QUEUE_STATUSES}) — oldest first
     * (FIFO), each with its items. For the kitchen queue (FARELO-059),
     * consumed by the future KDS screen. No pagination — same YAGNI
     * reasoning as {@link #listByCommand(int)}: the number of orders
     * simultaneously active (not yet {@code READY}) is naturally small.
     *
     * <p>Same N+1-items and {@code @Transactional(readOnly = true)}
     * reasoning as {@link #listByCommand(int)} — deliberately simple.
     */
    @Transactional(readOnly = true)
    public List<OrderWithItems> listQueue() {
        List<Order> orders = orderRepository.findByStatusInOrderByCreatedAtAsc(QUEUE_STATUSES);

        return orders.stream()
                .map(order -> new OrderWithItems(order, orderItemRepository.findByOrder(order)))
                .toList();
    }

    // Used by markAsPreparing/markAsReady below. See OrderRepository's
    // findByIdWithCommand javadoc for why it's not a plain findById.
    public Order getById(UUID id) {
        return orderRepository.findByIdWithCommand(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    /**
     * {@code CREATED} → {@code PREPARING} (FARELO-057). Valid origin
     * status: {@code CREATED} only — {@code CONFIRMED} is reserved in the
     * enum but has no transition into or out of it yet on the roadmap, so
     * this deliberately doesn't accept it as a starting point.
     */
    @Transactional
    public OrderWithItems markAsPreparing(UUID orderId) {
        return transition(orderId, OrderStatus.CREATED, OrderStatus.PREPARING);
    }

    /**
     * {@code PREPARING} → {@code READY} (FARELO-058). Valid origin status:
     * {@code PREPARING} only — going straight from {@code CREATED} skips
     * the kitchen's "in progress" signal, so it's rejected the same as any
     * other invalid origin.
     */
    @Transactional
    public OrderWithItems markAsReady(UUID orderId) {
        return transition(orderId, OrderStatus.PREPARING, OrderStatus.READY);
    }

    // Shared by markAsPreparing/markAsReady: fetch, validate origin status,
    // transition, record the OrderStatusHistory entry (FARELO-056's
    // mechanism), save — same read-check-write shape (and the same
    // unaddressed-concurrency caveat) as CommandService#open/close.
    private OrderWithItems transition(UUID orderId, OrderStatus requiredCurrentStatus, OrderStatus targetStatus) {
        Order order = getById(orderId);

        if (order.getStatus() != requiredCurrentStatus) {
            throw new OrderInvalidTransitionException(orderId, order.getStatus(), targetStatus);
        }

        order.setStatus(targetStatus);
        Order saved = orderRepository.save(order);
        orderStatusHistoryRepository.save(new OrderStatusHistory(saved, requiredCurrentStatus, targetStatus));

        List<OrderItem> items = orderItemRepository.findByOrder(saved);
        return new OrderWithItems(saved, items);
    }

}
