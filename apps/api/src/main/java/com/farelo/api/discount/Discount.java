package com.farelo.api.discount;

import com.farelo.api.command.Command;
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
 * A discount recorded against a {@link Command} (FARELO-230/231/232) — a
 * fixed amount or percentage reduction of the comanda's total, applied by
 * a staff member. First entity of the {@code discount} domain (see
 * docs/domain-model.md).
 *
 * <h2>Append-only ledger, same shape as {@code
 * com.farelo.api.payment.Payment}</h2>
 *
 * Every column {@code updatable = false}, no setters, no repository
 * update/delete usage — same reasoning as {@code Payment}'s javadoc: a
 * discount, once applied, is a completed fact (money the customer no
 * longer owes was decided at that moment); a wrong entry is corrected by a
 * future ticket's offsetting record, never an edit to this one.
 *
 * <h2>{@code originalAmount}/{@code discountedAmount} are frozen
 * snapshots</h2>
 *
 * {@code discountedAmount} is the actual reduction — for {@link
 * DiscountType#FIXED_AMOUNT}, exactly the requested amount; for {@link
 * DiscountType#PERCENTAGE}, {@code percentage}% of {@code originalAmount}
 * computed once at application time with {@link java.math.BigDecimal}
 * (FARELO-231's own requirement), never recomputed later even if the
 * comanda's items change afterward — same frozen-snapshot convention
 * {@code OrderItem#getUnitPrice()}/{@code Payment} already established.
 * {@code originalAmount} is the comanda's {@code totalOwed} ({@code
 * com.farelo.api.ordering.OrderService#getTotalOwed(int)}) at that same
 * moment — satisfies FARELO-232's explicit "valor original" audit
 * requirement, and is what a percentage discount's rate was computed
 * against (deliberately the raw total, not reduced by any discount already
 * applied — see {@code DiscountService} for the full reasoning).
 *
 * <h2>{@code appliedByUserId}/{@code appliedByUserName} — denormalized
 * operator, no {@code reason}-conditional requirement</h2>
 *
 * Same id+name pair {@code OrderItem#getCancelledByUserId()}/{@code
 * #getCancelledByUserName()} already established (FARELO-200/201):
 * survives a later user rename/deactivation, resolved via {@code
 * UserService#getById} in the service layer. {@code reason} (FARELO-232,
 * "motivo do desconto") is nullable/optional — see {@code
 * DiscountRequest}'s javadoc for why no obrigatory-vs-optional
 * configuration toggle exists.
 *
 * <p>Id generation: same strategy as every other entity in this codebase
 * ({@code @UuidGenerator(Style.RANDOM)} — see {@code Category}'s javadoc).
 */
@Entity
@Table(name = "discount")
public class Discount {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "command_id", nullable = false, updatable = false)
    private Command command;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private DiscountType type;

    // Nullable — only populated for PERCENTAGE (the rate used, e.g. 10.00
    // for "10%"). See class javadoc, "originalAmount/discountedAmount".
    @Column(name = "percentage", precision = 5, scale = 2, updatable = false)
    private BigDecimal percentage;

    @Column(name = "original_amount", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "discounted_amount", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal discountedAmount;

    @Column(name = "reason", updatable = false)
    private String reason;

    @Column(name = "applied_by_user_id", nullable = false, updatable = false)
    private UUID appliedByUserId;

    @Column(name = "applied_by_user_name", nullable = false, updatable = false)
    private String appliedByUserName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Discount() {
        // required by JPA
    }

    public Discount(
            Command command,
            DiscountType type,
            BigDecimal percentage,
            BigDecimal originalAmount,
            BigDecimal discountedAmount,
            String reason,
            UUID appliedByUserId,
            String appliedByUserName) {
        this.command = command;
        this.type = type;
        this.percentage = percentage;
        this.originalAmount = originalAmount;
        this.discountedAmount = discountedAmount;
        this.reason = reason;
        this.appliedByUserId = appliedByUserId;
        this.appliedByUserName = appliedByUserName;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public Command getCommand() {
        return command;
    }

    public DiscountType getType() {
        return type;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public BigDecimal getDiscountedAmount() {
        return discountedAmount;
    }

    public String getReason() {
        return reason;
    }

    public UUID getAppliedByUserId() {
        return appliedByUserId;
    }

    public String getAppliedByUserName() {
        return appliedByUserName;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Discount other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
