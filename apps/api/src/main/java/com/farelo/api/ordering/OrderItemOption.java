package com.farelo.api.ordering;

import com.farelo.api.catalog.ProductStepOption;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

/** Immutable choice snapshot: later catalog edits cannot change the sale. */
@Entity
@Table(name = "order_item_option")
public class OrderItemOption {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @Column(name = "step_id", nullable = false, updatable = false)
    private UUID stepId;
    @Column(name = "step_name", nullable = false, updatable = false)
    private String stepName;
    @Column(name = "option_id", nullable = false, updatable = false)
    private UUID optionId;
    @Column(name = "option_product_id", nullable = false, updatable = false)
    private UUID optionProductId;
    @Column(name = "option_name", nullable = false, updatable = false)
    private String optionName;
    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;
    @Column(name = "unit_price", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    protected OrderItemOption() { }

    public OrderItemOption(OrderItem item, ProductStepOption option, int quantity) {
        this.orderItem = item;
        this.stepId = option.getStep().getId();
        this.stepName = option.getStep().getLabel();
        this.optionId = option.getId();
        this.optionProductId = option.getOptionProduct().getId();
        this.optionName = option.getOptionProduct().getName();
        this.quantity = quantity;
        this.unitPrice = option.getPrice();
    }

    public OrderItemOption(OrderItem item, OrderItemOption source) {
        this.orderItem = item;
        this.stepId = source.stepId;
        this.stepName = source.stepName;
        this.optionId = source.optionId;
        this.optionProductId = source.optionProductId;
        this.optionName = source.optionName;
        this.quantity = source.quantity;
        this.unitPrice = source.unitPrice;
    }

    public UUID getStepId() { return stepId; }
    public String getStepName() { return stepName; }
    public UUID getOptionId() { return optionId; }
    public UUID getOptionProductId() { return optionProductId; }
    public String getOptionName() { return optionName; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
}
