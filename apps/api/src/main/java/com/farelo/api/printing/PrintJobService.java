package com.farelo.api.printing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.farelo.api.catalog.ProductionStation;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandService;
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderItem;
import com.farelo.api.ordering.OrderItemRepository;
import com.farelo.api.ordering.OrderNotFoundException;
import com.farelo.api.ordering.OrderRepository;
import com.farelo.api.ordering.OrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

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
 *
 * <h2>Retry ({@code FAILED} → {@code PENDING}, FARELO-079)</h2>
 *
 * The prompt mestre (seção 10) is explicit that a {@code FAILED} job should
 * allow retry, but until this ticket nothing actually implemented that: a
 * job stayed {@code FAILED} forever, since the Edge Agent only polls for
 * {@code PENDING} work ({@link #listPending()}).
 *
 * <p><b>Manual endpoint, not scheduled/automatic retry.</b> Two shapes were
 * considered: (a) a manual {@code POST /api/v1/print-jobs/{id}/retry} that
 * simply flips {@code FAILED} → {@code PENDING} on request, so the job
 * reappears in the next {@code GET /api/v1/print-jobs} poll; or (b) the
 * backend (or the Edge Agent) automatically rescheduling a failed job after
 * some delay, with its own attempt counter and backoff. This class
 * implements (a). Reasons:
 * <ul>
 *   <li>The Edge Agent (prompt mestre seção 11) already lists "manter fila
 *       temporária local" as a <em>future, not-yet-implemented</em>
 *       responsibility — there is no local queue/backoff mechanism on that
 *       side today for a scheduled retry to build on, and building one here
 *       in the backend instead would mean inventing a scheduling/timing
 *       policy (how long to wait, exponential backoff or not) with no real
 *       failure-pattern data to base it on yet.</li>
 *   <li>There is no consumer today asking for unattended automatic retry —
 *       every other endpoint in this domain follows the same YAGNI
 *       discipline already applied to {@code Printer}/{@code Ingredient}
 *       (deliberately minimal first cut, extended later once a concrete
 *       need shows up).</li>
 *   <li>A manual endpoint composes correctly with option (b) later: nothing
 *       here prevents a future ticket from adding a scheduler that simply
 *       calls this same {@link #retry(UUID)} method on a timer, once the
 *       Edge Agent's local queue (or some other driver) exists to decide
 *       when. Building the manual path first doesn't paint that option
 *       into a corner.</li>
 * </ul>
 *
 * <p><b>Retry limit.</b> An unbounded retry (an operator — today, a human;
 * the endpoint doesn't distinguish who calls it — clicking "retry" forever
 * on a job whose printer is simply gone) would leave a job cycling
 * {@code PENDING}/{@code FAILED} indefinitely with no signal that it is
 * stuck. {@link PrintJob#getRetryCount()} (new column, {@code
 * V18__add_print_job_retry_count_column.sql}) counts how many times a job
 * has been retried; {@link #retry(UUID)} rejects a further attempt once
 * {@code retryCount} reaches {@link #MAX_RETRY_COUNT}, via {@link
 * PrintJobRetryLimitExceededException} (distinct from {@link
 * PrintJobInvalidTransitionException} — see that exception's javadoc for
 * why). {@code MAX_RETRY_COUNT} is a small fixed constant rather than a
 * configurable/per-job value: nothing in this codebase today needs it to
 * vary (no consumer, no admin UI for it), so a config knob would be
 * speculative — same YAGNI reasoning as the rest of this section. Chosen
 * value (3) mirrors the kind of small fixed retry budget common for
 * transient hardware failures (printer momentarily offline/out of paper)
 * without letting a permanently broken printer accumulate print jobs
 * forever.
 */
@Service
public class PrintJobService {

    // See class javadoc, "Retry limit", for why this is a small fixed
    // constant rather than configurable.
    static final int MAX_RETRY_COUNT = 3;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PrintJobRepository printJobRepository;
    private final CommandService commandService;
    private final ObjectMapper objectMapper;

    public PrintJobService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PrintJobRepository printJobRepository,
            CommandService commandService,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.printJobRepository = printJobRepository;
        this.commandService = commandService;
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
            printJobs.add(printJobRepository.save(new PrintJob(order, serialize(content, "order " + orderId))));
        }
        return printJobs;
    }

    /**
     * Creates and persists one {@code PENDING}, {@link
     * PrintJobType#COMMAND_CHECK} {@link PrintJob} for a comanda's
     * "conferência" (FARELO-211) — a customer-facing pre-bill listing every
     * non-cancelled item across every non-{@code CANCELLED} order of the
     * command, with prices and a total. Backs {@code POST
     * /api/v1/commands/{number}/print-conference} (FARELO-212).
     *
     * <p>Excludes cancelled orders and, within a live order, individually
     * cancelled items — the exact same exclusion {@code
     * OrderItemRepository#sumOwedByCommand} applies for "what's owed"
     * (FARELO-200/201), reused here (via {@link CommandCheckContent#from})
     * rather than re-derived, per the project's Single Source of Truth
     * rule for a comanda's total/items.
     *
     * <p>Unlike {@link #createForOrder(UUID)} (triggered automatically by
     * {@code OutboxWorker} on {@code OrderCreated}), this is a deliberate,
     * on-demand staff action — a comanda may be "conferida" any number of
     * times as items are added, so there is no automatic trigger to hang
     * this off of.
     *
     * @throws com.farelo.api.command.CommandNotFoundException if no
     *         command exists for {@code commandNumber}.
     */
    @Transactional
    public PrintJob createCommandCheck(int commandNumber) {
        Command command = commandService.findByNumber(commandNumber);

        List<Order> orders = orderRepository.findByCommandOrderByCreatedAtAsc(command).stream()
                .filter(order -> order.getStatus() != OrderStatus.CANCELLED)
                .toList();

        List<OrderItem> items = orders.stream()
                .flatMap(order -> orderItemRepository.findByOrder(order).stream())
                .filter(item -> !item.isCancelled())
                .toList();

        CommandCheckContent content = CommandCheckContent.from(command, items);
        return printJobRepository.save(
                new PrintJob(command, serialize(content, "command " + commandNumber)));
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

    // Used by markPrinted/markFailed below, and by their shared transition
    // helper. Same JOIN FETCH reasoning as OrderService#getById — the order
    // association must be eagerly loaded here, since PrintJobResponse reads
    // job.getOrder().getId() in the controller, after this read's own
    // transaction has already closed.
    public PrintJob getById(UUID id) {
        return printJobRepository.findByIdWithAssociations(id)
                .orElseThrow(() -> new PrintJobNotFoundException(id));
    }

    /**
     * Marks a job successfully printed by the Edge Agent (FARELO-077):
     * {@code PENDING} → {@code PRINTED}. Reports the outcome of {@code POST
     * /api/v1/print-jobs/{id}/printed}.
     *
     * @throws PrintJobNotFoundException if no job exists for {@code id}.
     * @throws PrintJobInvalidTransitionException if the job's current
     *         status isn't {@code PENDING} — in particular, printing an
     *         already-{@code PRINTED} or already-{@code FAILED} job again
     *         is rejected rather than silently accepted.
     */
    @Transactional
    public PrintJob markPrinted(UUID id) {
        return transition(id, PrintJobStatus.PRINTED, PrintJob::markPrinted);
    }

    /**
     * Marks a job failed to print (e.g. the Edge Agent reported a printer
     * error, FARELO-077): {@code PENDING} → {@code FAILED}. Reports the
     * outcome of {@code POST /api/v1/print-jobs/{id}/failed}. No structured
     * failure reason is accepted — YAGNI, nothing consumes it yet; see
     * docs/domain-model.md, seção {@code printing}, for the full rationale.
     *
     * @throws PrintJobNotFoundException if no job exists for {@code id}.
     * @throws PrintJobInvalidTransitionException if the job's current
     *         status isn't {@code PENDING}, same as {@link
     *         #markPrinted(UUID)}.
     */
    @Transactional
    public PrintJob markFailed(UUID id) {
        return transition(id, PrintJobStatus.FAILED, PrintJob::markFailed);
    }

    /**
     * Moves a {@code FAILED} job back to {@code PENDING} (FARELO-079) so it
     * reappears in {@link #listPending()}/{@code GET /api/v1/print-jobs}
     * for the Edge Agent to attempt again. Reports the outcome of {@code
     * POST /api/v1/print-jobs/{id}/retry}. See class javadoc, "Retry", for
     * the full design rationale (manual endpoint, retry limit).
     *
     * <p>Not implemented via the shared {@link #transition(UUID,
     * PrintJobStatus, Consumer)} helper used by {@link #markPrinted(UUID)}/
     * {@link #markFailed(UUID)}: unlike those two (which both require the
     * same {@code PENDING} origin), this transition requires {@code FAILED}
     * as its origin <em>and</em> has an extra precondition (the retry
     * limit) that has nothing to do with the origin status — bolting that
     * onto the shared helper (which only knows how to compare a status and
     * call a mutator) would make it do two unrelated things. A small
     * dedicated method stays simpler to read than generalizing the helper
     * for a single caller.
     *
     * @throws PrintJobNotFoundException if no job exists for {@code id}.
     * @throws PrintJobInvalidTransitionException if the job's current
     *         status isn't {@code FAILED} — in particular, retrying a
     *         {@code PENDING} job (nothing to retry) or an already-{@code
     *         PRINTED} job is rejected rather than silently accepted.
     * @throws PrintJobRetryLimitExceededException if the job has already
     *         been retried {@link #MAX_RETRY_COUNT} times.
     */
    @Transactional
    public PrintJob retry(UUID id) {
        PrintJob job = getById(id);
        PrintJobStatus currentStatus = job.getStatus();

        if (currentStatus != PrintJobStatus.FAILED) {
            throw new PrintJobInvalidTransitionException(id, currentStatus, PrintJobStatus.PENDING);
        }
        if (job.getRetryCount() >= MAX_RETRY_COUNT) {
            throw new PrintJobRetryLimitExceededException(id, job.getRetryCount(), MAX_RETRY_COUNT);
        }

        job.retry();
        return printJobRepository.save(job);
    }

    // Shared by markPrinted/markFailed above: fetch, validate the job's
    // current status is PENDING (the only valid origin for either
    // transition — unlike OrderService#transition, no Set overload is
    // needed here since both callers require the exact same single origin
    // status), apply the entity mutation, save. The entity methods
    // (PrintJob#markPrinted/markFailed, FARELO-071) still do no validation
    // themselves — that responsibility lives here, same service/entity
    // split already established by OrderService#transition vs. Order's
    // plain setStatus.
    private PrintJob transition(UUID id, PrintJobStatus targetStatus, Consumer<PrintJob> mutator) {
        PrintJob job = getById(id);
        PrintJobStatus currentStatus = job.getStatus();

        if (currentStatus != PrintJobStatus.PENDING) {
            throw new PrintJobInvalidTransitionException(id, currentStatus, targetStatus);
        }

        mutator.accept(job);
        return printJobRepository.save(job);
    }

    // FARELO-210/211: takes Object (PrintJobContent or CommandCheckContent)
    // + a plain label for the error message, rather than two near-identical
    // overloads — the serialization/error-wrapping logic itself doesn't
    // care which record it's given.
    private String serialize(Object content, String context) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            // Same reasoning as OutboxPublisher#serialize: content is a
            // simple record built from already-persisted data, so reaching
            // here means Jackson genuinely can't serialize it — an
            // invariant violation, not an expected runtime condition.
            throw new IllegalStateException("Failed to serialize print job content for " + context, e);
        }
    }

}
