package com.farelo.api.cashier;

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
 * An ad-hoc cash-drawer movement recorded against a {@link CashSession},
 * not tied to any {@code Order}/{@code Command} — sangria/suprimento
 * (FARELO-310) or despesa/receita (FARELO-311), per {@link
 * CashMovementType}. First entity added after {@link CashSession} itself
 * (FARELO-307/308/309) in the {@code cashier} domain.
 *
 * <h2>Append-only ledger, same shape as {@code Payment}/{@code
 * Discount}</h2>
 *
 * Every column {@code updatable = false}, no setters beyond the
 * constructor, no repository {@code update}/{@code delete} usage anywhere
 * in this domain — same reasoning as {@code Payment}'s javadoc: once
 * recorded, a cash movement is a completed fact (money left/entered the
 * drawer, or was decided to). A wrong entry (wrong amount, wrong type) is
 * corrected by a new, offsetting record, never an edit to this one.
 *
 * <h2>{@code amount} is always a positive magnitude</h2>
 *
 * Same convention as {@code Payment#getAmount()} — the direction (cash
 * leaving vs. entering the drawer) comes entirely from {@link #getType()},
 * never from the sign of {@code amount} itself. A caller never sends a
 * negative number to represent a withdrawal.
 *
 * <h2>{@code createdByUserId}/{@code createdByUserName}: denormalized,
 * no FK</h2>
 *
 * Same id+name pair convention as {@code Discount#appliedByUserId}/{@code
 * #appliedByUserName} and {@code CashSession#openedByUserId}/{@code
 * #openedByUserName} — survives a later rename/deactivation of the {@code
 * User} row.
 *
 * <h2>Auditable</h2>
 *
 * FARELO-310's own text explicitly asks for this: {@code
 * CashMovementService#record} also writes an {@code AuditLog} entry (same
 * seam {@code PaymentService#record}/{@code OrderService#markAsCancelled}
 * already use) — see that method's javadoc.
 *
 * <p>Id generation: same strategy as every other entity in this codebase
 * ({@code @UuidGenerator(Style.RANDOM)}).
 */
@Entity
@Table(name = "cash_movement")
public class CashMovement {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cash_session_id", nullable = false, updatable = false)
    private CashSession cashSession;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private CashMovementType type;

    @Column(name = "amount", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "description", updatable = false)
    private String description;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "created_by_user_name", nullable = false, updatable = false)
    private String createdByUserName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected CashMovement() {
        // required by JPA
    }

    public CashMovement(
            CashSession cashSession,
            CashMovementType type,
            BigDecimal amount,
            String description,
            UUID createdByUserId,
            String createdByUserName) {
        this.cashSession = cashSession;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.createdByUserId = createdByUserId;
        this.createdByUserName = createdByUserName;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public CashSession getCashSession() {
        return cashSession;
    }

    public CashMovementType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public String getCreatedByUserName() {
        return createdByUserName;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CashMovement other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
