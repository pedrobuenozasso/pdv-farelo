package com.farelo.api.inventory;

import com.farelo.api.catalog.Product;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * The "header" of a {@link Product}'s recipe / ficha técnica (prompt mestre
 * seção 15): ties a product to the fact that it has (or had) a recipe —
 * e.g. "pão com ovos e bacon" consumes 3 UN ovos + 1 UN pão + 80 G bacon +
 * 10 G manteiga. Second entity of the {@code inventory} domain (see
 * docs/domain-model.md), building on {@link Ingredient} (FARELO-090).
 *
 * <p><b>FARELO-091 scope</b>: only this header. The actual list of
 * ingredients + quantities that make up the recipe ({@code RecipeItem}) is
 * FARELO-092, deliberately not modeled here — same incremental approach as
 * {@code Ingredient} not carrying stock balance fields until a ticket
 * needed them. No consumption logic either (FARELO-096, order creation
 * deducting stock) — this ticket only establishes that a recipe exists for
 * a product.
 *
 * <p><b>Relationship to {@link Product}: {@code @ManyToOne}, not
 * {@code @OneToOne}, plus a partial unique index.</b> The roadmap's stated
 * target shape is "one product has at most one *active* recipe at a time"
 * (prompt mestre doesn't ask for recipe history/versioning anywhere), which
 * argues for {@code @OneToOne}. But {@code @OneToOne} would force every
 * "change the recipe" operation to either mutate the one row in place (no
 * history — a `PUT`-only mutation, similar to `Ingredient`/`Product`, would
 * lose track of what proportions were being consumed before the mutation
 * happened) or delete-then-recreate, in either case discarding a natural,
 * low-cost audit trail. {@code @ManyToOne} plus {@code active} (same
 * pattern as {@code Category}/{@code Product}/{@code Printer}/
 * {@code Ingredient}) keeps every past recipe row around when a new one
 * replaces it — deactivate the old one, insert a new active one — at zero
 * extra modeling cost now (it's the exact same default field every other
 * entity in this codebase already carries) and with a real, if secondary,
 * benefit (a product's ingredient composition at a point in time is
 * reconstructable later, which matters once {@code InventoryMovement}
 * consumption references a specific recipe). The one-active-at-a-time rule
 * itself is still enforced — just via a partial unique index rather than
 * the FK cardinality — see {@code V17__create_recipe_table.sql} for the
 * {@code CREATE UNIQUE INDEX ... WHERE active} that does it at the DB
 * level (the actual source of truth for the constraint, since two
 * concurrent requests could otherwise both pass an application-level
 * "does an active recipe already exist" check before either commits);
 * {@link RecipeService#create(UUID)} also checks it first at the service
 * layer, both to fail fast without hitting the DB and to translate a
 * constraint violation into the same {@link RecipeAlreadyExistsException}
 * shape regardless of which of the two catches it first.
 *
 * <p>Id generation: same strategy as {@link Ingredient}/{@code Category}/
 * {@code Product}/{@code Printer} — Hibernate 6.6's {@code @UuidGenerator}
 * has no native UUIDv7 support, so {@code RANDOM} (UUIDv4) is used.
 */
@Entity
@Table(name = "recipe")
public class Recipe {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Recipe() {
        // required by JPA
    }

    public Recipe(Product product) {
        this.product = product;
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

    public Product getProduct() {
        return product;
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
        if (!(o instanceof Recipe other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
