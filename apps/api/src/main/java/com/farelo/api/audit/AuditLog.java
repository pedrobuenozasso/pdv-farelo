package com.farelo.api.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * A durable, append-only record of one sensitive operation — who did it,
 * when, to what, and (when applicable) what changed. FARELO-125, first
 * entity of the {@code audit} domain (see docs/domain-model.md). Prompt
 * mestre seção 27: "Operações sensíveis precisam registrar: quem, quando, o
 * quê, valor anterior, valor novo. Principalmente: preço, estoque,
 * cancelamento, pagamento, configuração fiscal, produto." Seção 26 lists
 * "audit log" among the mandatory security items.
 *
 * <p><b>This ticket only models the entity, its repository, and a minimal
 * read endpoint</b> — same "entity first, producers later" pattern already
 * used by {@code PrintJob} (FARELO-071), {@code Notification} (FARELO-110)
 * and {@code InventoryMovement} (FARELO-093) at their own first tickets.
 * Nothing constructs a real {@code AuditLog} yet: the actual producers —
 * auditing a price change (FARELO-126) and a stock adjustment (FARELO-127)
 * — are future tickets that will call {@link AuditLogService#record} from
 * inside {@code ProductService}/{@code InventoryMovementService}. This
 * ticket deliberately does not touch either of those classes.
 *
 * <h2>Design decision 1 — append-only, no {@code updatedAt}</h2>
 *
 * Same reasoning {@link com.farelo.api.inventory.InventoryMovement}'s
 * javadoc already gives for the stock ledger, restated here because it
 * applies just as literally: an audit record is a fact about a single
 * instant ("this happened"), not a mutable projection of current state.
 * Every column is {@code updatable = false}, this class exposes no setters,
 * and there is no repository {@code update}/{@code delete} usage anywhere in
 * this domain — only {@code save} for a new row and read queries (see
 * {@link AuditLogRepository}). Pairing that with an {@code updatedAt} that
 * could only ever equal {@code createdAt} would imply a row *could* be
 * revised in place — precisely the property an audit trail must not have
 * (a tamperable log is not an audit log). If a logged action turns out to
 * have been described wrong, the fix is a new corrective row, never an edit
 * to the original.
 *
 * <h2>Design decision 2 — who performed the action: a denormalized
 * snapshot, not a {@code @ManyToOne} to {@link com.farelo.api.security.User}
 * </h2>
 *
 * {@code userId}/{@code userName}/{@code userEmail} are plain columns
 * captured at the moment the action happened, not a live association. Same
 * "snapshot, not a live reference" reasoning already established by {@code
 * OrderItem.unitPrice} and {@code PrintJob.content} (see their javadocs): an
 * audit row must keep describing what an admin actually did, with the name/
 * email they had *at that time*, even if that {@code User} account is later
 * renamed, has its role changed, or (should a delete capability ever be
 * added — none exists today, only {@code User#active}) is deleted outright.
 * A {@code @ManyToOne} would make every one of those legitimate future edits
 * silently rewrite history it's this entity's entire job to keep truthful.
 *
 * <p>This is a deliberate step further than {@link
 * com.farelo.api.inventory.InventoryMovement#getOrderId()}, which stays a
 * plain {@code UUID} but still carries a DB-level foreign key to
 * {@code orders(id)} for referential integrity: that entity has no
 * human-readable snapshot of its own to fall back on, so the FK is the only
 * thing keeping the id meaningful. Here, {@code user_id} deliberately has
 * <b>no</b> foreign key to {@code app_user(id)} at all (see
 * {@code V25__create_audit_log_table.sql}) — {@code userName}/{@code
 * userEmail} already make each row self-describing independent of the
 * {@code app_user} row's continued existence, so an FK would buy nothing
 * beyond what's already captured by value, while creating a real risk: it
 * would let the audit trail — the one place this system is supposed to be
 * able to outlive the accounts it describes — block or complicate a future
 * user-deletion feature, which is exactly backwards for what an audit log is
 * for.
 *
 * <h2>Design decision 3 — {@code action}/{@code entityType} are plain
 * {@code String}s, not closed enums</h2>
 *
 * Contrast with {@link com.farelo.api.inventory.InventoryMovementType},
 * which <em>is</em> a closed enum: that was possible because prompt mestre
 * seção 13 already gave its full seven-value set upfront. Seção 27 gives no
 * such list for audit actions — only examples ("preço, estoque,
 * cancelamento, pagamento, configuração fiscal, produto") introduced by
 * "Principalmente" (mainly/primarily), i.e. an illustrative, not exhaustive,
 * set. This ticket has no visibility into the exact action vocabulary
 * FARELO-126 ({@code "PRICE_CHANGED"}, plausibly), FARELO-127 ({@code
 * "STOCK_ADJUSTED"}, plausibly) or any later auditing ticket will need —
 * hardcoding an enum now would mean guessing at their design, and every new
 * action type in the future would require a migration to widen a {@code
 * CHECK} constraint. A plain {@code VARCHAR} (see the migration) needs
 * neither: each future producer defines its own action/entity-type
 * vocabulary as plain string constants where it lives, the same way {@code
 * OutboxEvent.eventType}/{@code aggregateType} stay open {@code String}s for
 * an analogous reason (an unbounded, producer-defined set) rather than a
 * closed enum like {@code NotificationType}/{@code InventoryMovementType}
 * (each a small, fully-known-upfront set). {@code entityType} is expected to
 * be a simple entity name (e.g. {@code "Product"}, {@code "Ingredient"}) —
 * a convention for future producers to follow, not something this class
 * enforces.
 *
 * <h2>Design decision 4 — {@code previousValue}/{@code newValue}: nullable
 * JSONB snapshots, opaque to this ticket</h2>
 *
 * Seção 27 asks for "valor anterior, valor novo" without constraining their
 * shape, and different future producers will need different shapes: a price
 * change is plausibly one scalar ({@code {"price": 12.50}}), a stock
 * adjustment plausibly a small object (quantity delta, reason). Rather than
 * modeling a shape this ticket has no real producer to design against,
 * both fields follow the same "structured snapshot, no opinion on shape"
 * convention already established by {@code PrintJob.content}/{@code
 * OutboxEvent.payload}: mapped as a plain {@code String} with {@code
 * @JdbcTypeCode(SqlTypes.JSON)}, written into a {@code jsonb} column.
 * Nullable, unlike those two precedents — a creation has no meaningful
 * "before" state ({@code previousValue} stays {@code null}), and a
 * producer that only ever cares about "what it became" (or a deletion, only
 * "what it was") has a legitimate reason to leave the other {@code null}
 * too. Whoever builds a row (a future producer) is expected to serialize a
 * small JSON snapshot before constructing this entity, same division of
 * labor {@code PrintJob}'s javadoc already documents for its own {@code
 * content}.
 *
 * <p>Id generation: same strategy as every other entity in this codebase —
 * Hibernate 6.6's {@code @UuidGenerator} has no native UUIDv7 support, so
 * {@code RANDOM} (UUIDv4) is used.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "user_name", nullable = false, updatable = false)
    private String userName;

    @Column(name = "user_email", nullable = false, updatable = false)
    private String userEmail;

    @Column(name = "action", nullable = false, updatable = false)
    private String action;

    @Column(name = "entity_type", nullable = false, updatable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_value", updatable = false, columnDefinition = "jsonb")
    private String previousValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", updatable = false, columnDefinition = "jsonb")
    private String newValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected AuditLog() {
        // required by JPA
    }

    public AuditLog(
            UUID userId,
            String userName,
            String userEmail,
            String action,
            String entityType,
            UUID entityId,
            String previousValue,
            String newValue) {
        this.userId = userId;
        this.userName = userName;
        this.userEmail = userEmail;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.previousValue = previousValue;
        this.newValue = newValue;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getPreviousValue() {
        return previousValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AuditLog other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
