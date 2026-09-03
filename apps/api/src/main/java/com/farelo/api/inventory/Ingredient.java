package com.farelo.api.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * An ingredient used in a product's recipe (e.g. "Leite", "Café em grão",
 * "Copo 300ml"). First entity of the {@code inventory} domain (see
 * docs/domain-model.md) — the foundation for {@code Recipe}/{@code
 * RecipeItem} (FARELO-091/092) and the stock ledger, {@code
 * InventoryMovement} (FARELO-093).
 *
 * <p><b>FARELO-090 scope</b>: only the ingredient itself. No stock balance
 * fields ({@code currentStock}/{@code minimumStock}/{@code criticalStock} —
 * that's FARELO-095/099) and no unit cost (the prompt mestre doesn't ask for
 * ingredient pricing anywhere up to this ticket) — same deliberately-minimal
 * approach already taken for {@code Printer} (FARELO-070): add fields when a
 * concrete ticket needs them, not speculatively (AGENTS.md: não criar
 * abstrações prematuras).
 *
 * <p><b>FARELO-099 ("Criar estoque mínimo", prompt mestre seção 17) added
 * {@link #minimumStock}</b> — see that field's own javadoc for the full
 * design (nullable vs. defaulting-to-zero, and how it's read alongside
 * {@link InventoryMovementService#getBalance}). {@code currentStock} is
 * deliberately still absent: that concept is already the ledger-derived
 * balance ({@link InventoryMovementService#getBalance}, FARELO-095), never a
 * stored/mutable column (prompt mestre seção 13 — "O saldo deve ser
 * rastreável... nunca editado diretamente"). {@code criticalStock} (also
 * named in prompt mestre seção 17) remains out of scope too — a distinct,
 * unscheduled future ticket, not something FARELO-099 anticipates
 * speculatively.
 *
 * <p>Id generation: same strategy as {@code Category}/{@code Product}/
 * {@code Printer} — Hibernate 6.6's {@code @UuidGenerator} only supports
 * {@code AUTO}, {@code RANDOM} and {@code TIME} styles, no native UUIDv7
 * without an external library, so {@code RANDOM} (UUIDv4) is used.
 */
@Entity
@Table(name = "ingredient")
public class Ingredient {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false)
    private IngredientUnit unit;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * FARELO-099 ("Criar estoque mínimo"): the minimum-stock threshold below
     * which this ingredient is considered low. {@code NULL} — not {@code
     * ZERO} — means "no threshold configured yet"; a configured threshold of
     * exactly {@code 0} is itself a legitimate, deliberate choice (e.g. "flag
     * this ingredient the moment its balance goes negative") and must stay
     * distinguishable from "nobody has set a threshold for this ingredient at
     * all". This reads naturally alongside {@link
     * InventoryMovementService#getBalance}'s existing design: a balance of
     * {@code 0} already means something specific ("no movements yet", per
     * {@code IngredientBalance}'s javadoc) distinct from a missing value, so
     * the same nullable-means-"not set" convention is used here rather than
     * silently defaulting every ingredient to a {@code 0} threshold (which
     * would misleadingly claim someone had already decided a real value for
     * every ingredient, including ones nobody has configured). See {@link
     * IngredientBalance#isBelowMinimum()} for how this is actually compared
     * against a computed balance — {@code NULL} always means "never flagged
     * low", regardless of balance (including a negative one, reachable per
     * {@code InventoryMovementService#consumeForOrder}'s "no
     * stock-sufficiency check" design, FARELO-096/097).
     *
     * <p>{@code NUMERIC(12,3)} — same precision/scale as {@code
     * RecipeItem.quantity}/{@code InventoryMovement.quantity}, so it compares
     * directly against a computed balance without any scale mismatch.
     * Settable via {@code POST /api/v1/ingredients} (creation, optional) and
     * {@code PUT /api/v1/ingredients/{id}} (full replace — can also send
     * {@code null} to explicitly clear a previously-configured threshold),
     * same "optional field with no unambiguous default, so {@code PUT} must
     * be able to send {@code null} to clear it" shape already used by {@code
     * Product.productionStation}/{@code ProductUpdateRequest.productionStation}.
     */
    @Column(name = "minimum_stock", precision = 12, scale = 3)
    private BigDecimal minimumStock;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Ingredient() {
        // required by JPA
    }

    public Ingredient(String name, IngredientUnit unit) {
        this.name = name;
        this.unit = unit;
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

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public IngredientUnit getUnit() {
        return unit;
    }

    public void setUnit(IngredientUnit unit) {
        this.unit = unit;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public BigDecimal getMinimumStock() {
        return minimumStock;
    }

    public void setMinimumStock(BigDecimal minimumStock) {
        this.minimumStock = minimumStock;
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
        if (!(o instanceof Ingredient other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
