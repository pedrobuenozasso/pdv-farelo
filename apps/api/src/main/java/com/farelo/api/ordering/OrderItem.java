package com.farelo.api.ordering;

import com.farelo.api.catalog.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A line item within an {@link Order}: a quantity of a {@link Product} sold
 * at a specific price. Part of the {@code ordering} domain (see
 * docs/domain-model.md).
 *
 * <p><strong>Price snapshot</strong> (AGENTS.md convention): {@code
 * unitPrice} is a value frozen at the moment of sale — it is never derived
 * from {@code product.getPrice()}, and never updates if the product's
 * price changes later. This ticket (FARELO-051) only adds the column and
 * the entity; the logic that actually captures the current product price
 * automatically when an order item is created (and any related
 * validation, e.g. positive {@code quantity}) is FARELO-052/053, once the
 * endpoint exists.
 *
 * <p><strong>No {@code updatedAt}</strong> — deliberate, unlike every
 * other entity in this codebase so far: in this MVP an order item is
 * immutable once created (no edit flow yet; no endpoint changes its
 * quantity/product/price after creation). Adding {@code updatedAt} now
 * would be a column with no writer; it's cheaper to add later if/when an
 * update use case actually appears than to carry dead machinery now.
 *
 * <p>Id generation follows the same strategy as {@link
 * com.farelo.api.catalog.Category}.
 */
@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    // Frozen at sale time — never product.getPrice(). See class javadoc.
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected OrderItem() {
        // required by JPA
    }

    public OrderItem(Order order, Product product, int quantity, BigDecimal unitPrice) {
        this.order = order;
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OrderItem other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
