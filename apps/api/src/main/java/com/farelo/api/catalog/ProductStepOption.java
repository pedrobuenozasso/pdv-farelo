package com.farelo.api.catalog;

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

/** Price belongs to this link, not to the option product's current price. */
@Entity
@Table(name = "product_step_option")
public class ProductStepOption {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "step_id", nullable = false)
    private ProductStep step;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_product_id", nullable = false)
    private Product optionProduct;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    protected ProductStepOption() {
    }

    public ProductStepOption(ProductStep step, Product optionProduct, int displayOrder,
                             BigDecimal price) {
        this.step = step;
        this.optionProduct = optionProduct;
        this.displayOrder = displayOrder;
        this.price = price;
    }

    public UUID getId() { return id; }
    public ProductStep getStep() { return step; }
    public Product getOptionProduct() { return optionProduct; }
    public int getDisplayOrder() { return displayOrder; }
    public BigDecimal getPrice() { return price; }

    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public void setPrice(BigDecimal price) { this.price = price; }
}
