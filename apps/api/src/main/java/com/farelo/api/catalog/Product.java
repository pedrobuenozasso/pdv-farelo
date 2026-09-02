package com.farelo.api.catalog;

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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * A sellable item in the menu (e.g. "Café Espresso"). Part of the
 * {@code catalog} domain, the single source of truth for the menu (see
 * docs/domain-model.md).
 *
 * <p>Scope note (FARELO-011): no recipe, inventory or advanced fiscal
 * fields yet. {@code fiscalProfileId} (FARELO-151) is deliberately left out
 * for a later ticket. {@code availableOnMenu}/{@code availableOnPos} were
 * added in FARELO-017; {@code productionStation} was added in FARELO-073.
 *
 * <p>Id generation follows the same strategy as {@link Category} — see its
 * javadoc for why {@code RANDOM} (UUIDv4) is used instead of UUIDv7.
 */
@Entity
@Table(name = "product")
public class Product {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * Whether this product shows up on the QR menu (customer-facing). Can be
     * toggled independently of {@link #availableOnPos} — e.g. a product sold
     * out in-store might still be prepped for future menu display, or a POS-
     * only item (like a house tab adjustment) might never appear on the menu.
     */
    @Column(name = "available_on_menu", nullable = false)
    private boolean availableOnMenu = true;

    /**
     * Whether this product shows up on the POS (staff-facing), independent
     * of {@link #availableOnMenu} — see that field's javadoc.
     */
    @Column(name = "available_on_pos", nullable = false)
    private boolean availableOnPos = true;

    /**
     * Which physical station (e.g. {@code BAR}, {@code KITCHEN}) prepares
     * this product — used to route printed tickets per station once
     * FARELO-074 splits {@code PrintJob}s by it (see
     * {@link ProductionStation}'s javadoc for the full picture).
     *
     * <p><b>Nullable, unlike {@link #availableOnMenu}/{@link
     * #availableOnPos}</b>: those two booleans have one unambiguous safe
     * default ({@code true} — a new product should be visible everywhere
     * until told otherwise). A production station has no such default —
     * fabricating one (e.g. always {@code KITCHEN}) would be silently wrong
     * for a lot of products (a soda is neither obviously {@code BAR} nor
     * {@code KITCHEN} by some universal rule) and, once FARELO-074 starts
     * routing tickets by this field, a wrong default would misroute a
     * printed ticket without anyone having chosen that. {@code null} means
     * "not yet assigned" — staff sets it explicitly per product, same as
     * how {@code category} was already mandatory but this field is not.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "production_station")
    private ProductionStation productionStation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Product() {
        // required by JPA
    }

    public Product(String name, BigDecimal price, Category category) {
        this.name = name;
        this.price = price;
        this.category = category;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isAvailableOnMenu() {
        return availableOnMenu;
    }

    public void setAvailableOnMenu(boolean availableOnMenu) {
        this.availableOnMenu = availableOnMenu;
    }

    public boolean isAvailableOnPos() {
        return availableOnPos;
    }

    public void setAvailableOnPos(boolean availableOnPos) {
        this.availableOnPos = availableOnPos;
    }

    public ProductionStation getProductionStation() {
        return productionStation;
    }

    public void setProductionStation(ProductionStation productionStation) {
        this.productionStation = productionStation;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
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
        if (!(o instanceof Product other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
