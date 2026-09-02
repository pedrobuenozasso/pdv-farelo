package com.farelo.api.ordering;

import com.farelo.api.command.Command;
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
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * A customer's order within a {@link Command} (comanda). Part of the
 * {@code ordering} domain (see docs/domain-model.md).
 *
 * <p>Mapped to table {@code orders} (plural), not {@code order} —
 * {@code ORDER} is a reserved SQL keyword (used in {@code ORDER BY}), so an
 * unquoted {@code order} table name would break every raw SQL statement
 * that touches it (and every future migration referencing it, e.g.
 * {@code OrderItem}'s FK in FARELO-051). The migration file is still named
 * {@code V7__create_order_table.sql} per the ticket, but it creates the
 * {@code orders} table — see that file's comment.
 *
 * <p>Scope note (FARELO-050): no {@code OrderItem} yet (FARELO-051), no
 * price snapshot (FARELO-052), no endpoints (FARELO-053+).
 *
 * <p>Id generation follows the same strategy as {@link
 * com.farelo.api.catalog.Category} — see its javadoc for why {@code RANDOM}
 * (UUIDv4) is used instead of UUIDv7.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "command_id", nullable = false)
    private Command command;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status = OrderStatus.CREATED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Order() {
        // required by JPA
    }

    public Order(Command command) {
        this.command = command;
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

    public Command getCommand() {
        return command;
    }

    public void setCommand(Command command) {
        this.command = command;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
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
        if (!(o instanceof Order other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
