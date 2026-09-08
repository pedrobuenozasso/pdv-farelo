package com.farelo.api.command;

import com.farelo.api.table.DiningTable;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * A "comanda" (tab) that tracks a customer's order cycle. Part of the
 * {@code command} domain (see docs/domain-model.md).
 *
 * <p>{@code number} (1-100, see FARELO-031 for the seed) is the
 * human-facing identifier printed on the physical comanda and used by
 * staff/customers — it is deliberately separate from {@code id} (UUID),
 * which is a technical identifier and is never exposed as the public
 * business identifier (prompt mestre seções 7-8).
 *
 * <p>Commands are never deleted — each operational cycle (a customer's
 * visit) leaves a historical record. Reopening/history handling for a
 * number's past cycles is out of scope here and left for future tickets;
 * this entity only models the current row for a given {@code number}.
 *
 * <p>Id generation follows the same strategy as {@link
 * com.farelo.api.catalog.Category} — see its javadoc for why {@code RANDOM}
 * (UUIDv4) is used instead of UUIDv7.
 *
 * <p><b>{@code customerName}/{@code customerPhone}</b> (FARELO-190/191):
 * the single central "who is at this comanda right now" customer record —
 * editable directly by staff via PDV ({@code CommandService#updateCustomer})
 * and kept in sync whenever an order is created with a non-blank name/phone
 * ({@code OrderService#create}'s write-through). Deliberately not the same
 * thing as {@code Order.customerName}/{@code Order.customerPhone} — those
 * stay an immutable per-order snapshot (see that class's javadoc); these
 * two are the current, mutable value for the comanda as a whole. Both
 * nullable — "no customer info recorded (yet, or anymore)" is a legitimate
 * permanent state, not a placeholder. {@code customerPhone} is always
 * stored normalized (digits only, country code prepended when missing —
 * see {@code CommandService#normalizePhone}), never however staff typed
 * it.
 *
 * <p><b>{@code table}</b> (FARELO-315): an <b>optional</b> reference to the
 * physical {@link DiningTable} this comanda is currently associated with —
 * {@code null} means "no table" (delivery/balcão, cardápio QR without
 * table service), a legitimate and common permanent state, never a
 * placeholder to fill in later. This is deliberately the only direction of
 * this relationship: {@code DiningTable} has no reference back to {@code
 * Command} (see that class's javadoc) — a table can only ever be looked up
 * from a comanda, not the other way around, which keeps {@code
 * com.farelo.api.table}'s core (entity/repository/service) free of any
 * dependency on {@code command}. Written via {@code
 * CommandService#updateTable} ({@code PATCH
 * /api/v1/commands/{number}/table}), same full-replace convention as
 * {@code updateCustomer}. {@code Order} gains no equivalent field: it
 * already references {@code Command} (mandatory {@code @ManyToOne}), so a
 * table associated with the order's comanda is already reachable
 * transitively — a second, redundant reference directly on {@code Order}
 * would risk disagreeing with the one on {@code Command}, which is worse
 * than not having it.
 */
@Entity
@Table(name = "command")
public class Command {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "number", nullable = false, unique = true)
    private int number;

    // EnumType.STRING, never ORDINAL — storing the ordinal would silently
    // corrupt data if CommandStatus's declaration order ever changes.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CommandStatus status = CommandStatus.AVAILABLE;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_phone")
    private String customerPhone;

    // Optional — see this field's own javadoc above. EAGER, same
    // pragmatic choice already made project-wide for small,
    // frequently-needed @ManyToOne associations (avoids a
    // LazyInitializationException on CommandResponse#from reading
    // getTable() outside a transaction, the exact lesson FARELO-055's
    // JOIN FETCH already documents for a similar case).
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "table_id")
    private DiningTable table;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Command() {
        // required by JPA
    }

    public Command(int number) {
        this.number = number;
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

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public CommandStatus getStatus() {
        return status;
    }

    public void setStatus(CommandStatus status) {
        this.status = status;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public DiningTable getTable() {
        return table;
    }

    public void setTable(DiningTable table) {
        this.table = table;
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
        if (!(o instanceof Command other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
