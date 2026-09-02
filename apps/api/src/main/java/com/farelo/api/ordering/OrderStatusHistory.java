package com.farelo.api.ordering;

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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * An append-only record of one status transition of an {@link Order}
 * (AGENTS.md / prompt mestre seção 9: "Registrar mudanças em histórico.
 * Nunca depender apenas do status atual."). Part of the {@code ordering}
 * domain (see docs/domain-model.md).
 *
 * <p>{@code fromStatus} is nullable — the very first entry, written when
 * an order is created ({@code CREATED}), has no prior status to record.
 * {@code toStatus} is always present.
 *
 * <p><strong>No setters, unlike {@link OrderItem}</strong>: that entity is
 * "immutable for now" only because no edit flow exists yet at the API
 * level, but it still exposes setters for potential internal reuse.
 * {@code OrderStatusHistory} is an append-only audit trail by nature — a
 * past transition is a historical fact that should never be rewritten —
 * so this entity deliberately has no way to mutate a row after
 * construction, a stronger guarantee than "no endpoint happens to do it
 * yet."
 *
 * <p>Written today by {@link OrderService#create(int, java.util.List)}
 * (FARELO-056, {@code fromStatus = null} / {@code toStatus = CREATED});
 * future status transitions (FARELO-057/058 — {@code PREPARING},
 * {@code READY}) will each append their own entry through this same
 * mechanism.
 *
 * <p>Id generation follows the same strategy as {@link
 * com.farelo.api.catalog.Category}.
 */
@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    private OrderStatus toStatus;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private OffsetDateTime changedAt;

    protected OrderStatusHistory() {
        // required by JPA
    }

    public OrderStatusHistory(Order order, OrderStatus fromStatus, OrderStatus toStatus) {
        this.order = order;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    @PrePersist
    protected void onCreate() {
        this.changedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public OrderStatus getFromStatus() {
        return fromStatus;
    }

    public OrderStatus getToStatus() {
        return toStatus;
    }

    public OffsetDateTime getChangedAt() {
        return changedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OrderStatusHistory other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
