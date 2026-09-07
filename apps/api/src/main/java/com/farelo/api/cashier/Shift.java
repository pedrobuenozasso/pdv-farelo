package com.farelo.api.cashier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * A named, reusable template for opening a {@link CashSession} (FARELO-306,
 * "Turno") — e.g. "TURNO 01", pre-configured with an opening account name
 * ("Caixa Dinheiro") and a closing account name ("Tesouraria"), reused every
 * time a cashier opens a new cash session against it. First entity of the
 * new {@code cashier} domain (see docs/domain-model.md, "Domínio Caixa" in
 * docs/gaps-sischef-pdv.md) — deliberately isolated from {@code Order}/
 * {@code OrderItem}/{@code Command}: nothing in this domain touches any of
 * those, other than a future read of a comanda's total paid for
 * reconciliation (FARELO-309).
 *
 * <h2>{@code openingAccountName}/{@code closingAccountName} are plain
 * strings, not a foreign key to an account entity</h2>
 *
 * Sischef models these as references into its own chart-of-accounts/
 * bookkeeping module. This codebase has no such module (no {@code Account}
 * entity, no double-entry ledger anywhere) and the ticket text explicitly
 * warns against copying Sischef 1:1 without need — building one just to
 * back two labels on a template nobody else reads yet would be exactly the
 * kind of speculative abstraction this codebase avoids (AGENTS.md). Both
 * fields are free-text labels for now (e.g. "Caixa Dinheiro" /
 * "Tesouraria"); introducing a real account model is a future ticket's call
 * if a real bookkeeping integration ever needs one.
 *
 * <h2>{@code name} is unique</h2>
 *
 * Backed by {@code uk_shift_name} ({@code V39__create_shift_table.sql}) —
 * same reasoning as {@code app_user.email} (V20): a duplicate-named
 * template would be confusing for staff picking one when opening a cash
 * session, with no legitimate reason to have two "TURNO 01"s.
 *
 * <h2>{@code active}</h2>
 *
 * Same soft-deactivation convention as {@code Category}/{@code Product}/
 * {@code Ingredient}/{@code User} — lets a retired template stop being
 * offered as an option without deleting the historical record any
 * {@link CashSession} still references via its required {@code shift}
 * association.
 *
 * <p>Id generation: same strategy as every other entity in this codebase —
 * {@code @UuidGenerator(Style.RANDOM)}, no native UUIDv7 support in
 * Hibernate 6.6 (see {@code Category}'s javadoc).
 */
@Entity
@Table(name = "shift")
public class Shift {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "opening_account_name", nullable = false)
    private String openingAccountName;

    @Column(name = "closing_account_name", nullable = false)
    private String closingAccountName;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Shift() {
        // required by JPA
    }

    public Shift(String name, String openingAccountName, String closingAccountName) {
        this.name = name;
        this.openingAccountName = openingAccountName;
        this.closingAccountName = closingAccountName;
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

    public String getOpeningAccountName() {
        return openingAccountName;
    }

    public String getClosingAccountName() {
        return closingAccountName;
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
        if (!(o instanceof Shift other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
