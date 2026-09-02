package com.farelo.api.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * One line of a {@link Recipe}'s composition (prompt mestre seção 15): an
 * {@link Ingredient} and how much of it is consumed per unit sold of the
 * recipe's product — e.g. "pão com ovos e bacon" has one {@code RecipeItem}
 * per ingredient: 3 UN ovos, 1 UN pão, 80 G bacon, 10 G manteiga.
 * FARELO-092, following {@code Ingredient} (FARELO-090) and the {@code
 * Recipe} header (FARELO-091).
 *
 * <p><b>FARELO-092 scope</b>: only this line-item entity and its CRUD. No
 * consumption of stock when an order is created (FARELO-096, a future
 * ticket) — this entity only records the recipe's composition, it doesn't
 * act on it yet.
 *
 * <p><b>{@code quantity} is always in {@link Ingredient#getUnit()}'s base
 * unit</b> — same "prefer the base unit internally" rule already applied to
 * {@link IngredientUnit} itself (prompt mestre seção 14: "não misturar
 * unidade de estoque com descrição de embalagem — internamente preferir
 * unidade base"). No purchase-unit conversion is modeled here either; e.g.
 * 80 G bacon is stored as {@code 80} against an ingredient whose unit is
 * {@code GRAM}, never as some other purchase-facing unit. Converting to a
 * display/purchase unit (e.g. showing bacon in KG on a report) is UI/
 * reporting responsibility for a future ticket, not this one.
 *
 * <p><b>{@code quantity} is {@link BigDecimal}, never {@code double}/{@code
 * float}</b> (AGENTS.md convention, so far only applied to money elsewhere in
 * this codebase) — chosen here for the same reason it's right for money:
 * exact decimal arithmetic with no floating-point rounding surprises, and
 * this field legitimately needs fractional values too (e.g. {@code 0.5} L of
 * milk, stored as {@code 500} against a {@code MILLILITER} ingredient, or a
 * fraction of a gram of an expensive spice). Column is {@code NUMERIC(12,3)}
 * — three decimal places (finer than money's two) to comfortably represent
 * small fractional weights/volumes without needing sub-gram/sub-milliliter
 * precision beyond that; twelve total digits is far more headroom than any
 * realistic recipe quantity needs, matching the generous-but-bounded style
 * already used for money's {@code NUMERIC(10,2)}.
 *
 * <p><b>No collection of {@code RecipeItem} on {@link Recipe}, and {@code
 * Recipe.java} is intentionally left untouched by this ticket.</b> The
 * relationship is expressed as a plain unidirectional {@code @ManyToOne}
 * from this class to {@code Recipe}, not a bidirectional
 * {@code @OneToMany} on the {@code Recipe} side. Reasoning: (1) nothing
 * about "list the items of a recipe" needs a live, managed Hibernate
 * collection on the owning side — {@link RecipeItemRepository#findByRecipeId}
 * is a plain, explicit query that does the same job without any of a
 * bidirectional association's usual footguns (keeping both sides in sync on
 * add/remove, N+1 risk if the collection is EAGER, or a
 * {@code LazyInitializationException} risk if it's LAZY and read outside a
 * transaction — this codebase runs with {@code open-in-view=false}, so that
 * risk is real, see {@code PrintJobRepository}/{@code RecipeRepository}'s
 * {@code JOIN FETCH} precedent this class's own repository follows below);
 * (2) {@code Recipe.java} was merged very recently as its own isolated
 * ticket (FARELO-091) — touching it here for a convenience that isn't
 * required adds surface area to review and a small chance of colliding with
 * another agent's concurrent work on the same file, for no behavioral gain.
 * If a future ticket ever needs "cascade-delete a recipe's items when the
 * recipe itself is deleted", that's the point to revisit this — today
 * {@code Recipe} is never hard-deleted (only deactivated), so no such
 * cascade is needed yet either.
 *
 * <p>Id generation: same strategy as every other entity in this domain —
 * Hibernate 6.6's {@code @UuidGenerator} has no native UUIDv7 support, so
 * {@code RANDOM} (UUIDv4) is used.
 */
@Entity
@Table(name = "recipe_item")
public class RecipeItem {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected RecipeItem() {
        // required by JPA
    }

    public RecipeItem(Recipe recipe, Ingredient ingredient, BigDecimal quantity) {
        this.recipe = recipe;
        this.ingredient = ingredient;
        this.quantity = quantity;
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

    public Recipe getRecipe() {
        return recipe;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
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
        if (!(o instanceof RecipeItem other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
