package com.farelo.api.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * A record of something that needs to be — or already was — sent to a
 * recipient (FARELO-110). Today, in practice, that always means a WhatsApp
 * message: the prompt mestre (seção 19) says "Utilizar futuramente: Meta
 * WhatsApp Cloud API. Fluxo: {@code ORDER_READY → Notification Worker →
 * WhatsApp}. Notificações internas também poderão existir: estoque baixo,
 * estoque zerado, falha de impressão."
 *
 * <p><strong>This ticket only models the entity itself</strong> — the
 * durable record of "what needs to be sent, to whom, and whether it went
 * out" — not the sending mechanism. Nothing constructs a real {@code
 * Notification} yet (that requires a real trigger, e.g. reacting to {@code
 * ORDER_READY}, which is FARELO-112) and nothing sends one (the Meta
 * WhatsApp Cloud API adapter is FARELO-111). Same "entity-only first
 * ticket" scope {@code PrintJob} started with at FARELO-071.
 *
 * <h2>Design decision 1 — standalone domain entity, not derived from {@code
 * OutboxEvent}</h2>
 *
 * Two shapes were considered: (a) let a future consumer build a {@code
 * Notification} straight from an {@code OutboxEvent} it dispatches (the
 * same shape {@code com.farelo.api.outbox.OutboxWorker} already uses to
 * create a {@code PrintJob} for {@code OrderCreated}), with {@code
 * notification} depending on {@code outbox}'s types; or (b) an independent
 * domain entity that some future worker (FARELO-112/113) populates by
 * reacting to a domain event, with no dependency between the two packages
 * in either direction. This class takes (b) — the safer, lower-risk choice
 * for a ticket that builds no producer or consumer at all:
 *
 * <ul>
 *   <li>The {@code outbox} package's documented dependency direction (see
 *       its {@code package-info.java}) is that business domains depend on
 *       {@code outbox} to <em>publish</em>, and that {@code outbox} itself
 *       only depends forward into a domain (today, only {@code
 *       PrintJobService}) at the narrow point where it actually dispatches
 *       an event to real work. Nothing here dispatches anything yet — there
 *       is no second real consumer to justify {@code OutboxWorker} learning
 *       about {@code notification} today, exactly the same reasoning that
 *       package's javadoc already gives for why a generic handler registry
 *       isn't built yet with only one real consumer.</li>
 *   <li>{@code Notification} has no natural reason to reference {@code
 *       OutboxEvent} at all: an {@code OutboxEvent} row is an opaque
 *       {@code (aggregateType, aggregateId, eventType, payload)} record of
 *       something that already happened to an aggregate; a {@code
 *       Notification} is a record of an outbound message to a human, with
 *       its own recipient/content/delivery status that has nothing to do
 *       with outbox's own bookkeeping (e.g. {@code processedAt}). Coupling
 *       them would only serve a hypothetical future shape neither FARELO-112
 *       nor FARELO-113 has designed yet.</li>
 * </ul>
 *
 * <p><strong>Update, FARELO-112</strong>: the eventual link described above
 * now exists for {@code ORDER_READY} — {@code OutboxWorker} dispatches a
 * drained {@code OrderReady} event to {@code
 * com.farelo.api.notification.OrderReadyNotificationService#createForOrder},
 * which creates a {@code PENDING Notification} exactly the way {@code
 * PrintJobService} creates a {@code PrintJob} for {@code OrderCreated}. The
 * design choice above (b) held: {@code Notification} still has zero
 * dependency on {@code com.farelo.api.outbox}'s types in either direction —
 * the new service depends forward on {@code com.farelo.api.ordering.Order}
 * to read {@code customerPhone}, not on anything outbox-shaped. {@code
 * STOCK_LOW}/{@code STOCK_CRITICAL}/{@code OUT_OF_STOCK}/{@code
 * PRINT_FAILED} remain undesigned — FARELO-113's job, a future ticket.
 *
 * <h2>Design decision 2 — {@code recipient} lives directly on this entity,
 * no separate channel abstraction</h2>
 *
 * {@code recipient} is a plain {@code String} (a WhatsApp-formatted phone
 * number, e.g. {@code "5511999999999"}) rather than, say, a {@code channel}
 * enum plus a polymorphic address. The prompt mestre (seção 19) only ever
 * names one real channel — WhatsApp — for both the customer-facing flow
 * ({@code ORDER_READY}) and "notificações internas" (estoque baixo/zerado,
 * falha de impressão); nothing in that section suggests internal
 * notifications are a <em>different</em> channel, only a different {@code
 * recipient} (an internal staff number instead of the customer's). Adding a
 * {@code channel} field with a single possible value today would be
 * speculative structure with nothing real to validate it against — the same
 * YAGNI discipline already applied elsewhere in this codebase (e.g. {@code
 * Printer}'s first cut). The field is named {@code recipient}, not {@code
 * phoneNumber}, specifically so a genuinely new channel in the future (e.g.
 * email) would not force a rename — the generic name already leaves room
 * for that without pre-building an abstraction this ticket has no second
 * case to design against.
 *
 * <h2>Design decision 3 — {@code content} is a frozen, plain-text
 * snapshot</h2>
 *
 * Same "snapshot, not a live reference" reasoning as {@code
 * PrintJob#getContent()} (see its javadoc, "Design decision 2"): whatever
 * text a {@code Notification} was created with must never change later just
 * because, say, an order or a product is edited afterward — it is a record
 * of what was actually sent (or attempted), not a pointer to recompute from
 * current state.
 *
 * <p>Unlike {@link com.farelo.api.printing.PrintJob#getContent()}, this is a
 * plain {@code String} (a {@code TEXT} column), not JSON: {@code
 * PrintJob.content} is <em>structured</em> data (command number, a list of
 * items) because its consumer — a future Edge Agent formatting a physical
 * ticket — needs to read individual fields out of it. A {@code
 * Notification}'s consumer (a future WhatsApp adapter, FARELO-111) only
 * ever needs one thing: the final, already-formatted message body to hand
 * to the Meta WhatsApp Cloud API as-is. There is nothing for that consumer
 * to parse out of it, so wrapping it in a structured/JSON shape would add
 * indirection with no reader that needs it.
 *
 * <h2>Design decision 4 — {@code status}</h2>
 *
 * {@code PENDING}/{@code SENT}/{@code FAILED}, {@code
 * @Enumerated(EnumType.STRING)} (never {@code ORDINAL} — same reasoning as
 * {@code CommandStatus}/{@code OrderStatus}/{@code PrintJobStatus}: storing
 * the ordinal would silently corrupt data if this enum's declaration order
 * ever changes), default {@code PENDING}. See {@link NotificationStatus}
 * for the full rationale, including why no validated transition service
 * method exists yet.
 *
 * <p>{@link #markSent()}/{@link #markFailed()} are the only ways to change
 * {@code status} after creation (no public setter) — same "dumb mutator,
 * no validation" shape as {@link com.farelo.api.printing.PrintJob#markPrinted()}/
 * {@link com.farelo.api.printing.PrintJob#markFailed()} at their own
 * entity-only ticket (FARELO-071): there is no real caller yet (that's
 * FARELO-112/113), so adding transition validation now would be guessing at
 * a rule with no use case to test it against.
 *
 * <p>{@code updatedAt} exists (unlike, e.g., the append-only {@code
 * InventoryMovement} ledger, which deliberately has no {@code updatedAt} —
 * see docs/domain-model.md, seção {@code inventory}) precisely because
 * {@code Notification} does have state that changes over time ({@code
 * status}, {@code PENDING → SENT}/{@code FAILED}) — the same reasoning that
 * already gives {@code PrintJob} an {@code updatedAt} too.
 *
 * <p>Id generation follows the same strategy as {@link
 * com.farelo.api.catalog.Category} — see its javadoc for why {@code
 * RANDOM} (UUIDv4) is used instead of UUIDv7.
 */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private NotificationType type;

    @Column(name = "recipient", nullable = false, updatable = false)
    private String recipient;

    // Frozen at creation time, plain text — see class javadoc, "Design
    // decision 3", for why this differs from PrintJob.content's JSONB.
    @Column(name = "content", nullable = false, updatable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Notification() {
        // required by JPA
    }

    public Notification(NotificationType type, String recipient, String content) {
        this.type = type;
        this.recipient = recipient;
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
     * Marks this notification successfully delivered. No validation of the
     * current status here — same "no validation in the entity" split as
     * {@link com.farelo.api.printing.PrintJob#markPrinted()}; see class
     * javadoc, "Design decision 4".
     */
    public void markSent() {
        this.status = NotificationStatus.SENT;
    }

    /**
     * Marks this notification as failed to deliver. Same "no validation
     * here" reasoning as {@link #markSent()}.
     */
    public void markFailed() {
        this.status = NotificationStatus.FAILED;
    }

    public UUID getId() {
        return id;
    }

    public NotificationType getType() {
        return type;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getContent() {
        return content;
    }

    public NotificationStatus getStatus() {
        return status;
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
        if (!(o instanceof Notification other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
