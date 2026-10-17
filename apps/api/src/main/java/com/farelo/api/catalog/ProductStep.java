package com.farelo.api.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/** A selection step owned by one catalog product. No checkout semantics yet. */
@Entity
@Table(name = "product_step")
public class ProductStep {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Column(name = "min_choices", nullable = false)
    private int minChoices;

    @Column(name = "max_choices", nullable = false)
    private int maxChoices;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    // Preserve the source vocabulary without inventing checkout behavior.
    @Column(name = "level", nullable = false, length = 80)
    private String level;

    @Column(name = "charge_type", nullable = false, length = 40)
    private String chargeType;

    protected ProductStep() {
    }

    public ProductStep(Product product, String label, int minChoices, int maxChoices,
                       int displayOrder, String level, String chargeType) {
        this.product = product;
        this.label = label;
        this.minChoices = minChoices;
        this.maxChoices = maxChoices;
        this.displayOrder = displayOrder;
        this.level = level;
        this.chargeType = chargeType;
    }

    public UUID getId() { return id; }
    public Product getProduct() { return product; }
    public String getLabel() { return label; }
    public int getMinChoices() { return minChoices; }
    public int getMaxChoices() { return maxChoices; }
    public int getDisplayOrder() { return displayOrder; }
    public String getLevel() { return level; }
    public String getChargeType() { return chargeType; }

    public void setLabel(String label) { this.label = label; }
    public void setMinChoices(int minChoices) { this.minChoices = minChoices; }
    public void setMaxChoices(int maxChoices) { this.maxChoices = maxChoices; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public void setLevel(String level) { this.level = level; }
    public void setChargeType(String chargeType) { this.chargeType = chargeType; }
}
