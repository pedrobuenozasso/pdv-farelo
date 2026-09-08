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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * A cash-drawer session (FARELO-307/308/309, "Caixa") opened against a
 * {@link Shift} template by an operator. Unlike {@code Payment}/{@code
 * Discount}/{@code AuditLog} (append-only ledgers), this is a mutable,
 * stateful entity with a real lifecycle — open, then closed, then
 * (separately) reconciled — same shape as {@code Command}/{@code Order},
 * not those ledgers. See {@code docs/domain-model.md}, section {@code
 * cashier}, for the full design writeup.
 *
 * <h2>{@code shift}: unidirectional {@code @ManyToOne}, immutable</h2>
 *
 * Fixed at open time ({@code updatable = false}) — a session's template
 * never changes after opening. {@code Shift} gets no {@code @OneToMany}
 * back, same "no collection on the parent" convention every other
 * ledger-shaped/child entity in this codebase already follows.
 *
 * <h2>{@code openedByUserId}/{@code openedByUserName}, {@code
 * closedByUserId}/{@code closedByUserName}, {@code reconciledByUserId}/
 * {@code reconciledByUserName}: denormalized id+name pairs, no FK</h2>
 *
 * Same convention as {@code Discount#appliedByUserId}/{@code
 * #appliedByUserName} and {@code OrderItem#cancelledByUserId}/{@code
 * #cancelledByUserName} — survives a later rename/deactivation of the
 * {@code User} row, resolved via {@code UserService#getById} in the
 * service layer, never a live reference.
 *
 * <h2>Full column set declared from FARELO-307</h2>
 *
 * The closing columns ({@code closingAmount}/{@code closedAt}/{@code
 * closedByUserId}/{@code closedByUserName}) are only ever written by
 * {@code CashSessionService#close} (FARELO-308), and the reconciliation
 * columns ({@code expectedAmount}/{@code reconciliationDifference}/{@code
 * reconciledAt}/{@code reconciledByUserId}/{@code reconciledByUserName})
 * only by {@code #reconcile} (FARELO-309) — but both sets exist on this
 * entity already, unlike {@code fiscal_profile}'s incremental
 * ALTER-TABLE-per-ticket history (V30/V32/V33). See {@code
 * V40__create_cash_session_table.sql}'s header comment for the full
 * reasoning (mirrors {@code command.status}'s complete-CHECK-from-V5
 * precedent).
 *
 * <p>Id generation: same strategy as every other entity in this codebase
 * ({@code @UuidGenerator(Style.RANDOM)}).
 */
@Entity
@Table(name = "cash_session")
public class CashSession {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false, updatable = false)
    private Shift shift;

    @Column(name = "opened_by_user_id", nullable = false, updatable = false)
    private UUID openedByUserId;

    @Column(name = "opened_by_user_name", nullable = false, updatable = false)
    private String openedByUserName;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private OffsetDateTime openedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CashSessionStatus status = CashSessionStatus.OPEN;

    @Column(name = "closing_amount", precision = 10, scale = 2)
    private BigDecimal closingAmount;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "closed_by_user_id")
    private UUID closedByUserId;

    @Column(name = "closed_by_user_name")
    private String closedByUserName;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", nullable = false)
    private CashSessionReconciliationStatus reconciliationStatus = CashSessionReconciliationStatus.PENDING;

    @Column(name = "expected_amount", precision = 10, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "reconciliation_difference", precision = 10, scale = 2)
    private BigDecimal reconciliationDifference;

    @Column(name = "reconciled_at")
    private OffsetDateTime reconciledAt;

    @Column(name = "reconciled_by_user_id")
    private UUID reconciledByUserId;

    @Column(name = "reconciled_by_user_name")
    private String reconciledByUserName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected CashSession() {
        // required by JPA
    }

    public CashSession(Shift shift, UUID openedByUserId, String openedByUserName) {
        this.shift = shift;
        this.openedByUserId = openedByUserId;
        this.openedByUserName = openedByUserName;
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.openedAt = now;
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

    public Shift getShift() {
        return shift;
    }

    public UUID getOpenedByUserId() {
        return openedByUserId;
    }

    public String getOpenedByUserName() {
        return openedByUserName;
    }

    public OffsetDateTime getOpenedAt() {
        return openedAt;
    }

    public CashSessionStatus getStatus() {
        return status;
    }

    public void setStatus(CashSessionStatus status) {
        this.status = status;
    }

    public BigDecimal getClosingAmount() {
        return closingAmount;
    }

    public void setClosingAmount(BigDecimal closingAmount) {
        this.closingAmount = closingAmount;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(OffsetDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public UUID getClosedByUserId() {
        return closedByUserId;
    }

    public void setClosedByUserId(UUID closedByUserId) {
        this.closedByUserId = closedByUserId;
    }

    public String getClosedByUserName() {
        return closedByUserName;
    }

    public void setClosedByUserName(String closedByUserName) {
        this.closedByUserName = closedByUserName;
    }

    public CashSessionReconciliationStatus getReconciliationStatus() {
        return reconciliationStatus;
    }

    public void setReconciliationStatus(CashSessionReconciliationStatus reconciliationStatus) {
        this.reconciliationStatus = reconciliationStatus;
    }

    public BigDecimal getExpectedAmount() {
        return expectedAmount;
    }

    public void setExpectedAmount(BigDecimal expectedAmount) {
        this.expectedAmount = expectedAmount;
    }

    public BigDecimal getReconciliationDifference() {
        return reconciliationDifference;
    }

    public void setReconciliationDifference(BigDecimal reconciliationDifference) {
        this.reconciliationDifference = reconciliationDifference;
    }

    public OffsetDateTime getReconciledAt() {
        return reconciledAt;
    }

    public void setReconciledAt(OffsetDateTime reconciledAt) {
        this.reconciledAt = reconciledAt;
    }

    public UUID getReconciledByUserId() {
        return reconciledByUserId;
    }

    public void setReconciledByUserId(UUID reconciledByUserId) {
        this.reconciledByUserId = reconciledByUserId;
    }

    public String getReconciledByUserName() {
        return reconciledByUserName;
    }

    public void setReconciledByUserName(String reconciledByUserName) {
        this.reconciledByUserName = reconciledByUserName;
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
        if (!(o instanceof CashSession other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
