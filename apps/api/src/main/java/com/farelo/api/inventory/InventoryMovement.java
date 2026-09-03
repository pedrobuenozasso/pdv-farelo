package com.farelo.api.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * One row of the stock ledger (prompt mestre seção 13: "Não armazenar
 * apenas um número editável de saldo. Utilizar ledger:
 * {@code InventoryMovement}"). FARELO-093, following {@code Ingredient}
 * (FARELO-090), {@code Recipe} (FARELO-091) and {@code RecipeItem}
 * (FARELO-092).
 *
 * <p><b>Append-only, never mutated</b>: every stock adjustment — an entry, a
 * sale's consumption, a loss, a correction — is a brand-new row, positive or
 * negative; an {@link Ingredient}'s balance is the sum of all its rows, never
 * a field written in place (AGENTS.md: "Estoque: ledger via
 * {@code InventoryMovement}, nunca um saldo editável direto"). This is
 * enforced structurally, not just by convention: every column below is
 * {@code updatable = false}, and this class exposes no setters at all — once
 * constructed and persisted, an instance cannot be changed through this
 * entity's own API. There is deliberately no repository {@code update}/
 * {@code delete} usage anywhere in this domain either (only {@code save} for
 * a new row and read queries) — see {@link InventoryMovementRepository}.
 *
 * <p><b>{@code createdAt} only, no {@code updatedAt}</b> — a deliberate
 * divergence from every other entity in this codebase (which all pair
 * {@code createdAt} with {@code updatedAt}, see {@code Ingredient}/{@code
 * Recipe}/{@code RecipeItem}). An append-only ledger row has no "later
 * modified" concept to track: it is a fact about a single instant (this
 * movement happened), immutable from the moment it's written. Pairing it
 * with an {@code updatedAt} that could only ever equal {@code createdAt}
 * would be misleading — it would imply a row *could* be revised in place,
 * which is exactly the anti-pattern this entity exists to avoid (a mutable
 * balance). If a movement turns out to be wrong, the fix is a new
 * *offsetting* row (e.g. an {@code ADJUSTMENT} or {@code CANCELLATION}),
 * never an edit to the original — the ledger's history stays truthful.
 *
 * <p><b>{@code quantity} can be positive or negative</b>, always in {@link
 * Ingredient#getUnit()}'s base unit — same convention already established by
 * {@link RecipeItem#getQuantity()} (see its javadoc). A positive value is
 * stock coming in (e.g. {@code PURCHASE}); a negative value is stock going
 * out (e.g. {@code ORDER_CONSUMPTION}, {@code LOSS}). Modeling sign instead
 * of a separate "direction" flag keeps balance computation a plain
 * {@code SUM(quantity)} (see
 * {@link InventoryMovementRepository#sumQuantityByIngredientId}) with no
 * per-type branching needed by the reader — each producer (a future ticket)
 * decides its own sign when constructing the row. {@code BigDecimal}, never
 * {@code double}/{@code float} (AGENTS.md); column is {@code NUMERIC(12,3)},
 * same precision/scale as {@code RecipeItem.quantity} for the same reasoning
 * (three decimal places for small fractional weights/volumes).
 *
 * <p><b>{@code type}</b>: see {@link InventoryMovementType}'s javadoc for the
 * full enum and why it's used verbatim from the prompt mestre rather than
 * trimmed.
 *
 * <p><b>{@code orderId} — optional origin reference, preparing the ground
 * for FARELO-097's idempotency key, not implementing it</b>: prompt mestre
 * seção 16 gives the idempotency key literally as {@code ORDER_CONSUMPTION
 * orderId=123 ingredientId=5} — i.e. the natural key that must not be
 * double-processed is (conceptually) {@code (type, orderId, ingredientId)}
 * for order-sourced movements. This entity already has {@code type} and
 * {@code ingredient}; the piece it was missing is the order origin, so this
 * ticket adds {@code orderId} now to avoid a follow-up migration when
 * FARELO-097 needs a column to build a unique constraint against.
 *
 * <p>Deliberately a plain nullable {@code UUID} column — <b>not</b> a
 * {@code @ManyToOne} to {@code com.farelo.api.ordering.Order}, unlike the
 * cross-domain relation {@code com.farelo.api.printing.PrintJob} already has
 * to {@code Order}. Three reasons: (1) nothing reads an actual {@code Order}
 * field through this entity — no producer of {@code ORDER_CONSUMPTION} (or
 * any other order-related type) exists yet, so a lazy association would sit
 * permanently uninitialized, pure unused surface; (2) only {@code
 * ORDER_CONSUMPTION} (and plausibly {@code RETURN}/{@code CANCELLATION}, see
 * {@link InventoryMovementType}'s javadoc) would ever set it — {@code
 * PURCHASE}/{@code LOSS}/{@code ADJUSTMENT}/{@code INTERNAL_CONSUMPTION}
 * have no order origin at all, so a required relation would be wrong and an
 * optional {@code @ManyToOne} buys nothing a plain nullable column doesn't;
 * (3) it leaves the exact shape of the idempotency key itself
 * — e.g. whether it ends up being a real unique constraint on
 * {@code (type, order_id, ingredient_id)}, or something broader — as
 * FARELO-097's decision to make, not this ticket's. This ticket only adds
 * the column and a matching (non-unique) index; <b>no uniqueness constraint
 * is created here</b> — see
 * {@code V21__create_inventory_movement_table.sql} for the explicit note
 * that enforcing "no double-processing" is FARELO-097's responsibility, not
 * this one's. The column does carry a plain DB-level foreign key to
 * {@code orders(id)} for basic referential integrity when it is set (no
 * orphaned ids), which costs nothing today and needs no Java-side relation
 * to provide.
 *
 * <p>Id generation: same strategy as every other entity in this domain —
 * Hibernate 6.6's {@code @UuidGenerator} has no native UUIDv7 support, so
 * {@code RANDOM} (UUIDv4) is used.
 */
@Entity
@Table(name = "inventory_movement")
public class InventoryMovement {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false, updatable = false)
    private Ingredient ingredient;

    @Column(name = "quantity", nullable = false, updatable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private InventoryMovementType type;

    // Nullable — see class javadoc's "orderId" section for why only
    // order-sourced movement types are expected to set this, and why it's a
    // plain UUID rather than a @ManyToOne relation.
    @Column(name = "order_id", updatable = false)
    private UUID orderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected InventoryMovement() {
        // required by JPA
    }

    public InventoryMovement(Ingredient ingredient, BigDecimal quantity, InventoryMovementType type, UUID orderId) {
        this.ingredient = ingredient;
        this.quantity = quantity;
        this.type = type;
        this.orderId = orderId;
    }

    /**
     * Convenience constructor for movement types with no order origin (e.g.
     * {@code PURCHASE}, {@code LOSS}, {@code ADJUSTMENT}, {@code
     * INTERNAL_CONSUMPTION}) — same as calling the four-argument constructor
     * with a {@code null} {@code orderId}.
     */
    public InventoryMovement(Ingredient ingredient, BigDecimal quantity, InventoryMovementType type) {
        this(ingredient, quantity, type, null);
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public InventoryMovementType getType() {
        return type;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InventoryMovement other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
