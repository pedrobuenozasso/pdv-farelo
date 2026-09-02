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
