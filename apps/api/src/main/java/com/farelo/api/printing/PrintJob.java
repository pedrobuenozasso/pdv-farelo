package com.farelo.api.printing;

import com.farelo.api.command.Command;
import com.farelo.api.ordering.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * A request to print the kitchen/bar ticket for a specific {@link Order}.
 * Second entity of the {@code printing} domain (see docs/domain-model.md),
 * modeling the flow from the prompt mestre (seção 10): {@code Order criado
 * → PrintJob PENDING → Farelo Edge Agent → impressora → PRINTED} (or {@code
 * FAILED} on failure, allowing retry). Nothing calls the printer from
 * inside the HTTP transaction that creates an order — a {@code PrintJob}
 * row is the durable handoff point instead, the same "never call an
 * external system from inside the write transaction" shape the {@code
 * outbox} package already established for other side effects.
 *
 * <p><strong>This ticket (FARELO-071) only models the entity.</strong>
 * Nothing creates a {@code PrintJob} automatically when an {@code Order} is
 * created yet — that wiring is FARELO-072. Nothing routes a job to a
 * specific {@link Printer} yet either — that's per-{@code
 * productionStation} routing (FARELO-073/074, seção 12).
 *
 * <h2>Design decision 1 — references {@link Order}, not {@link Printer}</h2>
 *
 * A {@code PrintJob} exists to print the content of one specific order, so
 * {@code order} is a required {@code @ManyToOne} — same shape as {@link
 * com.farelo.api.ordering.OrderItem#getOrder()}. It does <em>not</em>
 * reference a {@code Printer}: which physical device a job goes to is a
 * routing decision (by {@code productionStation}, per item, seção 12) that
 * doesn't exist yet — modeling it now would be guessing at a shape FARELO-
 * 073/074 hasn't decided. A job with items from multiple stations may even
 * end up fanning out into more than one printed ticket at that point; tying
 * this entity to a single {@code Printer} today would work against that.
 *
 * <h2>Design decision 2 — {@code content} is a frozen snapshot, not a live
 * reference</h2>
 *
 * {@code content} stores what needs to be printed (command number, and each
 * item's product name/quantity) captured <em>at the moment the job is
 * created</em>, not just an {@code order} FK for a future consumer to
 * re-fetch. Same reasoning as the price snapshot on {@link
 * com.farelo.api.ordering.OrderItem#getUnitPrice()} and the item snapshot
 * inside {@link com.farelo.api.ordering.OrderCreatedEvent}: what was
 * ordered at print time must never change later just because, say, a
 * product gets renamed or an {@code OrderItem} row is edited by some future
 * feature. A printed kitchen ticket is itself a physical historical
 * record — it must reflect reality at the moment it was queued, not
 * whatever the database says whenever the Edge Agent happens to get around
 * to draining the queue (which could be seconds or, if a printer is down,
 * much longer).
 *
 * <p>There's a second, independent reason beyond snapshotting: the prompt
 * mestre (seção 11) is explicit that the Edge Agent "nunca deve possuir
 * regra de negócio de pedidos — é apenas infraestrutura de dispositivos"
 * (never holds order business logic — it's device infrastructure only). If
 * {@code PrintJob} only carried an {@code order} reference, the Edge Agent
 * (or whatever reads jobs for it) would need to know how to fetch an order,
 * walk its items, and format a ticket from them — that's business logic
 * about orders leaking into infrastructure. Embedding the fully-formed
 * snapshot keeps the Edge Agent's job dumb: read {@code content}, print it,
 * report the outcome.
 *
 * <p><strong>Storage</strong>: mapped as a plain {@code String} with {@code
 * @JdbcTypeCode(SqlTypes.JSON)}, written into a {@code jsonb} column — the
 * exact same convention {@link com.farelo.api.outbox.OutboxEvent#getPayload()}
 * already established for "structured snapshot data of a shape this class
 * doesn't need an opinion on": whoever builds a {@code PrintJob} (FARELO-
 * 072) serializes the snapshot (e.g. a small record with the command number
 * and a list of product name/quantity pairs) to JSON before constructing
 * this entity; this class just stores and returns the string as-is.
 *
 * <h2>Design decision 3 — {@code status}</h2>
 *
 * {@code PENDING}/{@code PRINTED}/{@code FAILED} (literal from seção 10),
 * {@code @Enumerated(EnumType.STRING)} (never {@code ORDINAL} — same
 * reasoning as {@code CommandStatus}/{@code OrderStatus}: storing the
 * ordinal would silently corrupt data if this enum's declaration order ever
 * changes), default {@code PENDING}. See {@link PrintJobStatus} for the
 * full rationale, including why no retry transition exists yet.
 *
 * <p>Id generation follows the same strategy as {@link
 * com.farelo.api.catalog.Category} — see its javadoc for why {@code
 * RANDOM} (UUIDv4) is used instead of UUIDv7.
 *
 * <h2>Design decision 4 — retry ({@code FAILED} → {@code PENDING},
 * FARELO-079)</h2>
 *
 * {@link #retry()} moves a {@code FAILED} job back to {@code PENDING} so it
 * reappears in {@code GET /api/v1/print-jobs} for the Edge Agent to attempt
 * again — the "permitindo retry" half of the prompt mestre's seção 10 flow,
 * left undesigned since FARELO-071/077 (see the note previously on {@link
 * PrintJobStatus}, now resolved here). See {@code
 * PrintJobService#retry(UUID)} for the full design rationale: why this is a
 * manual endpoint rather than an automatic scheduled retry, and why {@code
 * retryCount} exists with a maximum.
 *
 * <h2>Design decision 5 — {@code type} and the optional {@code order}/
 * {@code command} (FARELO-210/211)</h2>
 *
 * A second kind of job — {@link PrintJobType#COMMAND_CHECK}, the
 * "conferência" (customer-facing pre-bill for a whole {@link Command}, see
 * {@link CommandCheckContent}) — doesn't fit the original {@code
 * KITCHEN_TICKET} shape's assumption that a job always belongs to exactly
 * one {@link Order}: a conferência spans every order of a comanda. Rather
 * than a separate entity/table (which would mean the Edge Agent polling
 * two endpoints, and a second PENDING/PRINTED/FAILED/retry lifecycle to
 * keep in sync with this one), {@code order} became optional and a new
 * optional {@code command} was added alongside it — {@code type} says
 * which of the two is populated. The two constructors below each set
 * exactly one, and {@code ck_print_job_type_scope}
 * (V36__add_print_job_type_and_command_columns.sql) enforces that
 * exclusivity at the database level too, not just by convention.
 */
@Entity
@Table(name = "print_job")
public class PrintJob {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // Populated for KITCHEN_TICKET, null for COMMAND_CHECK — see class
    // javadoc, "Design decision 5".
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "order_id", nullable = true)
    private Order order;

    // Populated for COMMAND_CHECK, null for KITCHEN_TICKET — see class
    // javadoc, "Design decision 5".
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "command_id", nullable = true)
    private Command command;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private PrintJobType type;

    // Frozen at creation time — never re-derived from order/order items.
    // See class javadoc, "Design decision 2". Holds a serialized
    // PrintJobContent (KITCHEN_TICKET) or CommandCheckContent
    // (COMMAND_CHECK) depending on type — see PrintJobResponse#from for
    // where that branch happens.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PrintJobStatus status = PrintJobStatus.PENDING;

    // How many times this job has been moved back from FAILED to PENDING
    // via retry() — see "Design decision 4" above and
    // PrintJobService#retry(UUID) for the cap enforced against this value.
    // Starts at 0 (never retried), incremented only by retry() below.
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PrintJob() {
        // required by JPA
    }

    public PrintJob(Order order, String content) {
        this.order = order;
        this.type = PrintJobType.KITCHEN_TICKET;
        this.content = content;
    }

    // FARELO-210/211: the COMMAND_CHECK counterpart — see class javadoc,
    // "Design decision 5".
    public PrintJob(Command command, String content) {
        this.command = command;
        this.type = PrintJobType.COMMAND_CHECK;
        this.content = content;
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * Marks this job successfully printed by the Edge Agent. No validation
     * of the current status here (e.g. rejecting a transition out of an
     * already-terminal state) — that responsibility lives in the caller,
     * {@link PrintJobService#markPrinted(UUID)} (FARELO-077), same
     * service/entity split already established by {@code
     * com.farelo.api.ordering.OrderService#transition} vs. {@link
     * com.farelo.api.ordering.Order#setStatus}. Calling this method
     * directly (e.g. in a test, bypassing the service) still performs no
     * validation of its own.
     */
    public void markPrinted() {
        this.status = PrintJobStatus.PRINTED;
    }

    /**
     * Marks this job failed (e.g. the Edge Agent reported a printer error).
     * Same "no validation here, see {@link PrintJobService#markFailed(UUID)}"
     * reasoning as {@link #markPrinted()}. See {@link PrintJobStatus} for
     * why no retry-to-{@code PENDING} transition exists yet.
     */
    public void markFailed() {
        this.status = PrintJobStatus.FAILED;
    }

    /**
     * Moves this job back from {@code FAILED} to {@code PENDING} and bumps
     * {@code retryCount} by one, so it reappears in the Edge Agent's poll
     * for pending work (FARELO-079). Same "no validation here" split as
     * {@link #markPrinted()}/{@link #markFailed()}: whether the current
     * status is actually {@code FAILED} and whether {@code retryCount} is
     * still under the allowed maximum are both checked by the caller,
     * {@link PrintJobService#retry(UUID)}, not here.
     */
    public void retry() {
        this.status = PrintJobStatus.PENDING;
        this.retryCount++;
    }

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public Command getCommand() {
        return command;
    }

    public PrintJobType getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public PrintJobStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PrintJob other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
