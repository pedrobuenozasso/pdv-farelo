package com.farelo.api.printing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.farelo.api.catalog.ProductionStation;
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderItem;
import com.farelo.api.ordering.OrderItemRepository;
import com.farelo.api.ordering.OrderNotFoundException;
import com.farelo.api.ordering.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds and persists the {@link PrintJob}s for a given {@link Order}
 * (FARELO-072, split per station in FARELO-074). Called by {@code
 * com.farelo.api.outbox.OutboxWorker} when it dispatches an {@code
 * OrderCreated} outbox event — see that class's javadoc for the dispatch
 * mechanism and for what happens to the enclosing outbox batch if this
 * class throws.
 *
 * <h2>Design decision — content comes from the database, not the event
 * payload</h2>
 *
 * {@code OrderCreatedEvent} (the outbox event's payload) carries each
 * item's {@code productId}, {@code quantity} and {@code unitPrice} — but
 * not the product's <em>name</em> (or its {@code productionStation}), and
 * {@link PrintJob#getContent()} needs both to make a legible, correctly
 * routed ticket. Two ways to get it:
 *
 * <ol>
 *   <li>(a) Deserialize the event payload and batch-fetch products by id (a
 *       new "find products by ids" method, since none exists today); or</li>
 *   <li>(b) ignore the payload for this purpose and re-fetch the {@link
 *       Order} and its {@link OrderItem}s straight from the database by
 *       {@code aggregateId} (the order id) — the same {@code JOIN FETCH
 *       product} query ({@link OrderItemRepository#findByOrder}) the rest
 *       of the {@code ordering} domain already relies on to avoid {@code
 *       LazyInitializationException} when reading {@code
 *       item.getProduct().getName()}/{@code getProductionStation()}.</li>
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
 *
 * <h2>Splitting by station (FARELO-074)</h2>
 *
 * {@code createForOrder} groups an order's items by {@link
 * com.farelo.api.catalog.Product#getProductionStation() productionStation}
 * and creates one {@link PrintJob} per group — matching the prompt mestre
 * example verbatim (seção 12): 2 Cappuccino + 1 Coca-Cola (both {@code
 * BAR}) print on one ticket, 1 Croissant ({@code KITCHEN}) prints on
 * another. An order whose items all share one station (or have none
 * assigned) still produces exactly one {@code PrintJob} — the grouping
 * collapses to a single entry, so the common case does not regress into
 * always fanning out multiple rows.
 *
 * <p><b>Items with no station assigned</b> ({@code productionStation ==
 * null} on the product, see its javadoc): grouped manually with a plain
 * {@code Map#computeIfAbsent}/loop, deliberately <em>not</em> {@code
 * Collectors.groupingBy} — that collector's classifier rejects a {@code
 * null} key with an explicit {@code NullPointerException} ({@code
 * "element cannot be mapped to a null key"}, since Java 9), which would
 * make an order containing any unassigned-station item fail outright
 * instead of printing. A plain {@code HashMap}, unlike {@code
 * groupingBy}'s accumulator, has no such restriction — {@code
 * computeIfAbsent(null, ...)} works exactly like any other key. These
 * items land together in their own group instead of being silently
 * dropped from printing; that group still becomes a real {@code
 * PrintJob}, with {@link PrintJobContent#productionStation()} explicitly
 * {@code null} — see that record's javadoc for why silently dropping
 * unassigned items was rejected (staff would lose track of what to
 * prepare) in favor of a clearly-flagged-but-still-printed ticket, and
 * why a fabricated default station (e.g. always routing to {@code
 * KITCHEN}) was also rejected (same reasoning {@code
 * Product.productionStation} itself already rejected a default — see its
 * javadoc: a wrong default would misroute a ticket without anyone having
 * chosen that).
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
     * Creates and persists one {@code PENDING} {@link PrintJob} per {@link
     * ProductionStation} present among the order's items (plus, if any item's
     * product has no station assigned, one more job for that group — see
     * class javadoc), each with {@code content} snapshotting the command
     * number and that group's item names/quantities as of right now.
     *
     * @return the created jobs, one per station group found (always at least
     *         one — an order always has at least one item, enforced at
     *         creation by {@code OrderRequest.items}' {@code @NotEmpty}).
     * @throws OrderNotFoundException if no order exists for {@code
     *         orderId} — not expected in practice (the order was written
     *         in the same transaction that published the {@code
     *         OrderCreated} event this method is invoked for), but not
     *         guarded against beyond letting it propagate; see {@code
     *         OutboxWorker}'s javadoc for the resulting behavior.
     */
    @Transactional
    public List<PrintJob> createForOrder(UUID orderId) {
        Order order = orderRepository.findByIdWithCommand(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        List<OrderItem> items = orderItemRepository.findByOrder(order);

        // Not Collectors.groupingBy: its classifier throws NullPointerException
        // on a null key (since Java 9) — see class javadoc, "Items with no
        // station assigned". A plain HashMap#computeIfAbsent has no such
        // restriction, so an item with no productionStation groups cleanly
        // instead of blowing up the whole order's print job creation.
        Map<ProductionStation, List<OrderItem>> itemsByStation = new LinkedHashMap<>();
        for (OrderItem item : items) {
            itemsByStation.computeIfAbsent(item.getProduct().getProductionStation(), key -> new ArrayList<>())
                    .add(item);
        }

        List<PrintJob> printJobs = new ArrayList<>();
        for (Map.Entry<ProductionStation, List<OrderItem>> group : itemsByStation.entrySet()) {
            PrintJobContent content = PrintJobContent.from(order, group.getKey(), group.getValue());
            printJobs.add(printJobRepository.save(new PrintJob(order, serialize(content, orderId))));
        }
        return printJobs;
    }

    /**
     * Lists every {@code PENDING} {@link PrintJob}, oldest first (FIFO) —
     * backs {@code GET /api/v1/print-jobs} (FARELO-076), the Edge Agent's
     * (FARELO-075) source for what still needs to be printed. No pagination,
     * same YAGNI reasoning already applied to the kitchen queue ({@code
     * OrderService#listQueue}, {@code GET /api/v1/orders}) — print volume is
     * naturally low. {@code @Transactional(readOnly = true)}, same reasoning
     * as {@code OrderService#listQueue}: without it, the repository call
     * below runs in its own short transaction that has already closed by the
     * time the controller builds the response — the {@code JOIN FETCH} in
     * {@link PrintJobRepository#findByStatusOrderByCreatedAtAsc} is what
     * actually prevents {@code LazyInitializationException} there; this
     * annotation just makes the read one logical unit, consistent with the
     * rest of this class.
     */
    @Transactional(readOnly = true)
    public List<PrintJob> listPending() {
        return printJobRepository.findByStatusOrderByCreatedAtAsc(PrintJobStatus.PENDING);
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
