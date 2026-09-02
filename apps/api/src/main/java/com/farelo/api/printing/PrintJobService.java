package com.farelo.api.printing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderItem;
import com.farelo.api.ordering.OrderItemRepository;
import com.farelo.api.ordering.OrderNotFoundException;
import com.farelo.api.ordering.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Builds and persists the {@link PrintJob} for a given {@link Order}
 * (FARELO-072). Called by {@code com.farelo.api.outbox.OutboxWorker} when
 * it dispatches an {@code OrderCreated} outbox event — see that class's
 * javadoc for the dispatch mechanism and for what happens to the
 * enclosing outbox batch if this class throws.
 *
 * <h2>Design decision — content comes from the database, not the event
 * payload</h2>
 *
 * {@code OrderCreatedEvent} (the outbox event's payload) carries each
 * item's {@code productId}, {@code quantity} and {@code unitPrice} — but
 * not the product's <em>name</em>, and {@link PrintJob#getContent()} needs
 * a human-readable name to make a legible ticket. Two ways to get it:
 *
 * <ol>
 *   <li>(a) Deserialize the event payload and batch-fetch product names by
 *       id (a new "find products by ids" method, since none exists today);
 *       or</li>
 *   <li>(b) ignore the payload for this purpose and re-fetch the {@link
 *       Order} and its {@link OrderItem}s straight from the database by
 *       {@code aggregateId} (the order id) — the same {@code JOIN FETCH
 *       product} query ({@link OrderItemRepository#findByOrder}) the rest
 *       of the {@code ordering} domain already relies on to avoid {@code
 *       LazyInitializationException} when reading {@code
 *       item.getProduct().getName()}.</li>
 * </ol>
 *
 * <p>This class takes (b): it reuses existing, already-tested fetch logic
 * (the same query {@code CommandOrdersController} and the kitchen queue
 * already depend on) instead of duplicating it behind a new batch-fetch
 * method that nothing else needs yet. It also means this class was never
 * really a consumer of the event payload's shape — only of the {@code
 * aggregateId} that names which order to look up — so {@code
 * OrderCreatedEvent} stays free to change independent of what printing
 * needs from it.
 */
@Service
public class PrintJobService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PrintJobRepository printJobRepository;
    private final ObjectMapper objectMapper;

    public PrintJobService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PrintJobRepository printJobRepository,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.printJobRepository = printJobRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates and persists a {@code PENDING} {@link PrintJob} for the
     * order identified by {@code orderId}, with {@code content}
     * snapshotting the command number and each item's product name/
     * quantity as of right now (see class javadoc).
     *
     * @throws OrderNotFoundException if no order exists for {@code
     *         orderId} — not expected in practice (the order was written
     *         in the same transaction that published the {@code
     *         OrderCreated} event this method is invoked for), but not
     *         guarded against beyond letting it propagate; see {@code
     *         OutboxWorker}'s javadoc for the resulting behavior.
     */
    @Transactional
    public PrintJob createForOrder(UUID orderId) {
        Order order = orderRepository.findByIdWithCommand(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        List<OrderItem> items = orderItemRepository.findByOrder(order);

        PrintJobContent content = PrintJobContent.from(order, items);
        return printJobRepository.save(new PrintJob(order, serialize(content, orderId)));
    }

    private String serialize(PrintJobContent content, UUID orderId) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            // Same reasoning as OutboxPublisher#serialize: PrintJobContent
            // is a simple record built from already-persisted data, so
            // reaching here means Jackson genuinely can't serialize it —
            // an invariant violation, not an expected runtime condition.
            throw new IllegalStateException("Failed to serialize print job content for order " + orderId, e);
        }
    }

}
