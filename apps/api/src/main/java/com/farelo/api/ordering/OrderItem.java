package com.farelo.api.ordering;

import com.farelo.api.catalog.Product;
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
 * <p><strong>Cancellation</strong> (FARELO-200/201): {@code cancelledAt}
 * (nullable — non-null <em>is</em> "cancelled", no separate boolean) plus
 * {@code cancelledByUserId}/{@code cancelledByUserName} (operator, name
 * denormalized the same way {@code AuditLog.userName} is, so it survives a
 * later rename/deactivation) and {@code cancelReason}/{@code
 * cancelDescription} (see {@link OrderItemCancelReason}'s javadoc — {@code
 * cancelDescription} is required only when the reason is {@code OTHER}).
 * The item row is never deleted — same soft-state convention as {@code
 * Category}/{@code Ingredient}/{@code Recipe}'s {@code active} flag,
 * applied here via a nullable timestamp instead since "when" is itself
 * meaningful data FARELO-200 asks to keep. See {@link
 * OrderService#cancelItem}'s javadoc for the full precondition/scope
 * reasoning, including why no inventory reversal happens here (FARELO-203,
 * a distinct future ticket).
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

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancelled_by_user_id")
    private UUID cancelledByUserId;

    @Column(name = "cancelled_by_user_name")
    private String cancelledByUserName;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason")
    private OrderItemCancelReason cancelReason;

    @Column(name = "cancel_description")
    private String cancelDescription;

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

    public boolean isCancelled() {
        return cancelledAt != null;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public UUID getCancelledByUserId() {
        return cancelledByUserId;
    }

    public String getCancelledByUserName() {
        return cancelledByUserName;
    }

    public OrderItemCancelReason getCancelReason() {
        return cancelReason;
    }

    public String getCancelDescription() {
        return cancelDescription;
    }

    /**
     * The only writer of the five cancellation fields — deliberately one
     * method setting all of them together (no individual setters), so a
     * cancelled item can never end up with e.g. a {@code cancelReason} but
     * no {@code cancelledAt}. See {@link OrderService#cancelItem} for the
     * precondition checks (item not already cancelled, parent order not
     * terminal) that must happen before this is ever called.
     */
    public void cancel(
            UUID actorId,
            String actorName,
            OrderItemCancelReason reason,
            String description) {
        this.cancelledAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.cancelledByUserId = actorId;
        this.cancelledByUserName = actorName;
        this.cancelReason = reason;
        this.cancelDescription = description;
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
