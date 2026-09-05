package com.farelo.api.ordering;

import com.farelo.api.audit.AuditLogService;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductNotAvailableException;
import com.farelo.api.catalog.ProductService;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandService;
import com.farelo.api.inventory.InventoryMovementService;
import com.farelo.api.inventory.OrderItemConsumption;
import com.farelo.api.outbox.OutboxPublisher;
import com.farelo.api.security.User;
import com.farelo.api.security.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {

    // Statuses that still need kitchen attention, for the kitchen queue
    // (FARELO-059, listQueue below): everything before READY. CONFIRMED
    // has no transition into or out of it yet on the roadmap (see
    // OrderStatus/markAsPreparing's javadoc), but is included here since
    // conceptually it's still "not yet in the kitchen's hands", same as
    // CREATED. DELIVERED/CANCELLED are terminal (see markAsDelivered/
    // markAsCancelled below) and were never in this list — an order
    // reaching either already disappears from the queue with no change
    // needed here.
    private static final List<OrderStatus> QUEUE_STATUSES =
            List.of(OrderStatus.CREATED, OrderStatus.CONFIRMED, OrderStatus.PREPARING);

    // Valid origin statuses for markAsCancelled below: every non-terminal
    // status. DELIVERED and CANCELLED are terminal and deliberately
    // excluded — cancelling an already-delivered order, or cancelling one
    // twice, is rejected the same as any other invalid transition.
    private static final Set<OrderStatus> CANCELLABLE_STATUSES =
            Set.of(OrderStatus.CREATED, OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY);

    // Item-level statuses that block a cancellation (FARELO-200): the two
    // Order terminal statuses. See OrderItemCancellationNotAllowedException's
    // javadoc for why this is deliberately a different, wider set than
    // CANCELLABLE_STATUSES above (a different question: "is this order
    // settled" vs. "can this whole order still transition").
    private static final Set<OrderStatus> ITEM_CANCELLATION_BLOCKED_STATUSES =
            Set.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED);

    // FARELO-305: audit action/entity-type constants for markAsCancelled
    // below, same naming convention ProductService (AUDIT_ACTION_PRICE_CHANGED)/
    // InventoryMovementService already established.
    private static final String AUDIT_ACTION_ORDER_CANCELLED = "ORDER_CANCELLED";
    private static final String AUDIT_ENTITY_TYPE_ORDER = "Order";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final CommandService commandService;
    private final ProductService productService;
    private final OutboxPublisher outboxPublisher;
    private final InventoryMovementService inventoryMovementService;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            CommandService commandService,
            ProductService productService,
            OutboxPublisher outboxPublisher,
            InventoryMovementService inventoryMovementService,
            UserService userService,
            AuditLogService auditLogService,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.commandService = commandService;
        this.productService = productService;
        this.outboxPublisher = outboxPublisher;
        this.inventoryMovementService = inventoryMovementService;
        this.userService = userService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
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
     * own entries the same way. Also publishes an {@code OrderCreated}
     * outbox event ({@link OrderCreatedEvent}) via {@link OutboxPublisher}
     * — FARELO-060, the first real Transactional Outbox integration — in
     * this same transaction, so it commits or rolls back together with
     * everything above.
     *
     * <p>Once every item is persisted (so {@code order.getId()} is
     * available), also consumes each sold product's active recipe via
     * {@link InventoryMovementService#consumeForOrder(java.util.UUID,
     * List)} (FARELO-096, prompt mestre seção 16) — still inside this same
     * {@code @Transactional} method, the same "one more thing after order
     * creation" shape already established by the outbox publish below: if
     * writing a stock movement fails, order creation rolls back with it
     * rather than leaving an order whose stock was never deducted. A
     * product with no active recipe simply produces no movement for that
     * item — not every product needs one yet (see {@link
     * InventoryMovementService#consumeForOrder(java.util.UUID, List)}'s own
     * javadoc for the full quantity-math/idempotency-deferral reasoning).
     *
     * <p>{@code customerName}/{@code customerPhone} are optional
     * (nullable) — a plain snapshot on the order itself, not a
     * {@code customer} domain lookup; see {@link Order}'s javadoc.
     */
    @Transactional
    public OrderWithItems create(
            int commandNumber, List<NewOrderItem> newItems, String customerName, String customerPhone) {
        Command command = commandService.openForOrdering(commandNumber);

        // FARELO-190/191: write-through to the comanda's central customer
        // record whenever this order actually carries a name/phone — see
        // CommandService#applyCustomerInfoIfProvided's javadoc for why
        // this is the mechanism that makes the QR checkout and a PDV edit
        // resolve to "the same central information" (FARELO-191's
        // acceptance criterion), and why it's a no-op (not a blank-out)
        // when neither argument is provided, e.g. a PDV manual item entry
        // (FARELO-182), which collects no customer info at all.
        commandService.applyCustomerInfoIfProvided(command, customerName, customerPhone);

        Order order = orderRepository.save(new Order(command, customerName, customerPhone));
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

        // FARELO-096: consume each sold product's active recipe (if any)
        // now that every OrderItem is persisted and order.getId() is
        // available — see this method's javadoc and
        // InventoryMovementService#consumeForOrder's own javadoc.
        List<OrderItemConsumption> consumption = items.stream()
                .map(item -> new OrderItemConsumption(item.getProduct().getId(), item.getQuantity()))
                .toList();
        inventoryMovementService.consumeForOrder(order.getId(), consumption);

        OrderWithItems result = new OrderWithItems(order, items);
        outboxPublisher.publish("Order", order.getId(), "OrderCreated", OrderCreatedEvent.from(result));

        return result;
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

    /**
     * FARELO-143 ("Validar total pago antes de fechar"): a comanda's total
     * bill — the sum of {@code unitPrice * quantity} across every {@link
     * OrderItem} of every {@link Order} belonging to it, via {@link
     * OrderItemRepository#sumOwedByCommand}. This is the "total owed" half
     * of the comparison {@code PaymentService#closeCommand} needs against
     * {@code PaymentService#getTotalPaid} (FARELO-142, the "total paid"
     * half) before allowing {@code CommandService#close}.
     *
     * <p><b>Built here, in {@code ordering}, not in {@code payment}</b>: this
     * is fundamentally {@code Order}/{@code OrderItem} data (their prices
     * and quantities), the same reasoning already used for every other
     * computation in this class ({@link #listByCommand(int)}, {@link
     * #listQueue()}) — {@code payment} owning it would mean reaching across
     * into {@code ordering}'s entities to compute something that has
     * nothing to do with payments themselves. Confirmed before writing this:
     * {@code FARELO-142}'s own javadoc on {@code PaymentService#getTotalPaid}
     * explicitly left this uncomputed anywhere in the codebase — this method
     * is the first one.
     *
     * <p><b>{@code CANCELLED} orders are excluded.</b> A cancelled order was
     * never fulfilled — nothing was delivered, so there is nothing to
     * charge for it; counting it toward "total owed" would let a comanda
     * get stuck unable to close (total owed inflated by cancelled items
     * nobody expects to be paid for) or, worse, require a customer to pay
     * for food they never received. Every other {@code OrderStatus}
     * (including still-in-flight ones like {@code CREATED}/{@code
     * PREPARING}/{@code READY}) counts toward the bill — an order doesn't
     * need to reach {@code DELIVERED} to be owed; it needs to not be
     * cancelled. Implemented as {@code status <> CANCELLED} (an exclusion),
     * not an explicit inclusion list, so a future new {@code OrderStatus}
     * value defaults to being billable unless a future ticket decides
     * otherwise — the same "closed set of one exception" shape is easier to
     * reason about than enumerating every non-cancelled status by hand.
     *
     * <p>A comanda with zero orders (or only cancelled ones) reports {@code
     * 0} (never {@code null}), same {@code COALESCE(SUM(...), 0)} shape as
     * {@code PaymentRepository#sumAmountByCommand} — this is what makes an
     * always-{@code AVAILABLE}-then-{@code OPEN}-but-never-ordered-against
     * comanda still closable: {@code totalPaid (0) >= totalOwed (0)} holds
     * trivially.
     *
     * <p>Not status-gated on the {@code Command} itself, same reasoning as
     * {@link com.farelo.api.payment.PaymentService#getTotalPaid(int)}: this
     * is a pure read over already-recorded data, callable regardless of the
     * comanda's current status.
     *
     * @throws com.farelo.api.command.CommandNotFoundException {@code
     *     commandNumber} doesn't exist.
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalOwed(int commandNumber) {
        Command command = commandService.findByNumber(commandNumber);
        return orderItemRepository.sumOwedByCommand(command, OrderStatus.CANCELLED);
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
     *
     * <p>Also publishes an {@code OrderReady} outbox event ({@link
     * OrderReadyEvent}) via {@link OutboxPublisher} (FARELO-112) in this
     * same transaction, once the transition itself has succeeded — the
     * second real integration of the Transactional Outbox mechanism after
     * {@code OrderCreated} (FARELO-060). {@code
     * com.farelo.api.outbox.OutboxWorker} eventually dispatches it to
     * {@code com.farelo.api.notification.OrderReadyNotificationService},
     * which creates a {@code PENDING} {@code Notification} for the
     * customer — or skips creating one entirely if the order has no {@code
     * customerPhone} (see that class's javadoc). This method always
     * publishes the event regardless of whether the order has a phone
     * number on file: "an order became ready" is a fact worth recording
     * either way, and whether there's anyone to notify about it is a
     * decision the consumer makes at dispatch time, not something this
     * method needs to know about.
     *
     * <p>Published <em>after</em> {@link #transition(UUID, OrderStatus,
     * OrderStatus)} returns successfully (not before) — publishing first
     * would record an {@code OrderReady} event for a transition that could
     * still fail its own validation (e.g. an order not currently {@code
     * PREPARING}). Both writes still commit or roll back together: this
     * whole method remains one {@code @Transactional} unit, same as {@link
     * #create(int, List, String, String)}'s single-transaction shape.
     */
    @Transactional
    public OrderWithItems markAsReady(UUID orderId) {
        OrderWithItems result = transition(orderId, OrderStatus.PREPARING, OrderStatus.READY);
        outboxPublisher.publish("Order", result.order().getId(), "OrderReady", OrderReadyEvent.from(result));
        return result;
    }

    /**
     * {@code READY} → {@code DELIVERED}. Valid origin status: {@code READY}
     * only — same single-origin shape as {@link #markAsPreparing(UUID)}/
     * {@link #markAsReady(UUID)}, so it reuses the existing single-status
     * {@link #transition(UUID, OrderStatus, OrderStatus)} overload as-is.
     */
    @Transactional
    public OrderWithItems markAsDelivered(UUID orderId) {
        return transition(orderId, OrderStatus.READY, OrderStatus.DELIVERED);
    }

    /**
     * Cancels an order. Unlike {@link #markAsDelivered(UUID)} and the
     * FARELO-057/058 transitions, cancellation is valid from <em>any</em>
     * non-terminal status ({@link #CANCELLABLE_STATUSES}: {@code CREATED},
     * {@code CONFIRMED}, {@code PREPARING}, {@code READY}) — cancelling an
     * order makes sense regardless of how far it got through the kitchen,
     * as long as it hasn't reached a terminal status yet. {@code DELIVERED}
     * and {@code CANCELLED} are terminal and rejected as any other invalid
     * origin would be.
     *
     * <p>This doesn't fit the single-{@code requiredCurrentStatus} shape of
     * {@link #transition(UUID, OrderStatus, OrderStatus)}, so it uses the
     * {@link #transition(UUID, Set, OrderStatus)} overload instead (see that
     * method's javadoc for why an overload, rather than changing the
     * existing single-status method's signature).
     *
     * <p>{@code actorId} (FARELO-305, "Auditoria por usuário"): cancelling
     * an order is exactly the kind of sensitive action the prompt mestre
     * (seção 27) names — "cancelamento" — so this now records an {@link
     * com.farelo.api.audit.AuditLog} entry, same {@code
     * AuditLogService#record} seam {@code ProductService}/{@code
     * InventoryMovementService} already use for their own sensitive
     * actions, rather than a bespoke denormalized actor column on {@code
     * Order} (unlike {@code OrderItem#cancel}, FARELO-200/201 — that one
     * predates this ticket and its own column stays; no need to migrate it
     * onto the generic ledger retroactively).
     */
    @Transactional
    public OrderWithItems markAsCancelled(UUID orderId, UUID actorId) {
        OrderStatus previousStatus = getById(orderId).getStatus();
        OrderWithItems result = transition(orderId, CANCELLABLE_STATUSES, OrderStatus.CANCELLED);

        User actor = userService.getById(actorId);
        auditLogService.record(
                actor, AUDIT_ACTION_ORDER_CANCELLED, AUDIT_ENTITY_TYPE_ORDER, orderId,
                serializeStatus(previousStatus), serializeStatus(OrderStatus.CANCELLED));

        return result;
    }

    // FARELO-305: same "record + writeValueAsString" pattern ProductService#
    // serializePrice/PrintJobService#serialize already use for an
    // AuditLog/PrintJob snapshot — OrderCancellationSnapshot is a simple
    // record built from an already-valid OrderStatus, so a
    // JsonProcessingException here would mean Jackson genuinely can't
    // serialize it, an invariant violation rather than an expected runtime
    // condition.
    private String serializeStatus(OrderStatus status) {
        try {
            return objectMapper.writeValueAsString(new OrderCancellationSnapshot(status));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize order status snapshot", e);
        }
    }

    /**
     * Cancels one {@link OrderItem} without touching the rest of its
     * {@link Order} (FARELO-200/201) — the item-level counterpart to
     * {@link #markAsCancelled}, which cancels an order wholesale. The item
     * row is never deleted; {@link OrderItem#cancel} marks it (see that
     * method's javadoc for the full field set this writes: timestamp,
     * operator, reason, description).
     *
     * <p><b>Deliberately does NOT recalculate anything server-side</b> —
     * there is no "order total"/"comanda total" column anywhere in this
     * schema to update; every total in this system (comanda total owed,
     * an order's subtotal) is already computed on read, either by {@link
     * OrderItemRepository#sumOwedByCommand} (which already excludes whole
     * {@code CANCELLED} orders and, from this ticket on, must also treat a
     * cancelled item as excluded — see that query) or client-side in the
     * PDV (which now also filters {@code OrderItemResponse.cancelled()}).
     * FARELO-200's "recalcular valor da comanda" requirement is satisfied
     * by every reader correctly excluding this item going forward, not by
     * a stored total this method would need to touch.
     *
     * <p><b>Deliberately does NOT reverse any inventory movement</b> — an
     * item whose product had an active recipe already consumed ingredients
     * ({@link InventoryMovementService#consumeForOrder}, {@code
     * ORDER_CONSUMPTION}) the moment the order was created, and that
     * consumption is untouched here. Reversing it is FARELO-203 ("Reverter
     * estoque ao cancelar item"), a distinct, future ticket — this method
     * only does what FARELO-200/201 themselves ask for. Until FARELO-203
     * lands, cancelling an item leaves the ingredient ledger overstating
     * consumption for that item's quantity; this is a known, intentional
     * gap, not an oversight.
     *
     * @throws OrderNotFoundException {@code orderId} doesn't exist.
     * @throws OrderItemNotFoundException {@code itemId} doesn't exist, or
     *     exists but belongs to a different order.
     * @throws OrderItemCancellationNotAllowedException the parent order is
     *     {@code DELIVERED} or {@code CANCELLED} (see {@link
     *     ITEM_CANCELLATION_BLOCKED_STATUSES}).
     * @throws OrderItemAlreadyCancelledException the item was already
     *     cancelled.
     */
    @Transactional
    public OrderItem cancelItem(
            UUID orderId,
            UUID itemId,
            OrderItemCancelReason reason,
            String description,
            UUID actorId) {
        Order order = getById(orderId);

        OrderItem item = orderItemRepository.findByIdWithProduct(itemId)
                .filter(candidate -> candidate.getOrder().getId().equals(orderId))
                .orElseThrow(() -> new OrderItemNotFoundException(orderId, itemId));

        if (ITEM_CANCELLATION_BLOCKED_STATUSES.contains(order.getStatus())) {
            throw new OrderItemCancellationNotAllowedException(orderId, itemId, order.getStatus());
        }
        if (item.isCancelled()) {
            throw new OrderItemAlreadyCancelledException(itemId);
        }

        User actor = userService.getById(actorId);
        item.cancel(actor.getId(), actor.getName(), reason, description);

        return orderItemRepository.save(item);
    }

    // Single-origin convenience overload, used by markAsPreparing/
    // markAsReady/markAsDelivered — unchanged in signature and behavior
    // from FARELO-057/058, now just a thin wrapper around the Set-based
    // overload below.
    private OrderWithItems transition(UUID orderId, OrderStatus requiredCurrentStatus, OrderStatus targetStatus) {
        return transition(orderId, Set.of(requiredCurrentStatus), targetStatus);
    }

    // Shared by every transition above: fetch, validate the order's
    // current status is one of validCurrentStatuses, transition, record
    // the OrderStatusHistory entry (FARELO-056's mechanism), save — same
    // read-check-write shape (and the same unaddressed-concurrency caveat)
    // as CommandService#open/close.
    //
    // A Set<OrderStatus> overload, rather than changing the single-status
    // method above, because markAsCancelled (multiple valid origins) needs
    // a genuinely different shape than markAsPreparing/markAsReady/
    // markAsDelivered (exactly one valid origin each): every existing
    // caller keeps passing a single OrderStatus with no change at all,
    // and the Set-based method is where the one new piece of logic
    // (membership check instead of equality) actually lives.
    private OrderWithItems transition(UUID orderId, Set<OrderStatus> validCurrentStatuses, OrderStatus targetStatus) {
        Order order = getById(orderId);
        OrderStatus currentStatus = order.getStatus();

        if (!validCurrentStatuses.contains(currentStatus)) {
            throw new OrderInvalidTransitionException(orderId, currentStatus, targetStatus);
        }

        order.setStatus(targetStatus);
        Order saved = orderRepository.save(order);
        orderStatusHistoryRepository.save(new OrderStatusHistory(saved, currentStatus, targetStatus));

        List<OrderItem> items = orderItemRepository.findByOrder(saved);
        return new OrderWithItems(saved, items);
    }

}
