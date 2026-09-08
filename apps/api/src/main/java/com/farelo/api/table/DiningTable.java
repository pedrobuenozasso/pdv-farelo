package com.farelo.api.table;

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
 * FARELO-315: a physical table in the café's dining room ("Mesa" in the
 * Sischef comparison, {@code docs/gaps-sischef-pdv.md} item 3 — "Mesa como
 * conceito separado de Comanda"). New domain, {@code table}, not
 * previously listed in {@code docs/domain-model.md}'s domain table.
 *
 * <p><b>Naming: {@code DiningTable}, not {@code Table}</b> — this class
 * lives in a JPA entity annotated {@code @Table(name = "dining_table")}
 * (below); naming the class itself {@code Table} would collide with the
 * {@code jakarta.persistence.Table} annotation this very file already
 * imports for that purpose. The database table is named {@code
 * dining_table} for the same reason {@code Order} maps to the {@code
 * orders} table (see that class's javadoc): {@code TABLE} is a reserved
 * SQL keyword, and an unquoted table literally named {@code table} would
 * break raw SQL that references it, including future migrations.
 *
 * <p><b>Deliberately separate from {@link com.farelo.api.command.Command}
 * — a table and a comanda are different lifecycles</b>: a table is
 * physical furniture that exists whether or not anyone is using it right
 * now; a comanda is a customer's order cycle (see {@code Command}'s
 * javadoc). The Sischef comparison that motivated this ticket makes this
 * exact distinction ("o Sischef tem Mesa e Comanda como entidades
 * diferentes, mas vinculáveis"). {@code Command} gains an <b>optional</b>
 * reference to this entity (see that class's own {@code table} field) —
 * never the reverse, and never required: a comanda not tied to any table
 * (delivery/balcão, cardápio QR without table service) must keep working
 * exactly as it does today. This ticket does not make a table's link
 * mandatory anywhere.
 *
 * <p>{@code number} (not {@code id}) is the human-facing business
 * identifier staff use, same convention as {@code Command.number} — see
 * that field's javadoc. Unlike {@code Command} (seeded 1-100 by a
 * migration, FARELO-031), tables are created and numbered through the API
 * itself ({@code DiningTableService#create}), so uniqueness is enforced in
 * the service layer (see that method's javadoc) rather than relying only
 * on the database's {@code UNIQUE} constraint for a friendly error.
 *
 * <p>{@code capacity} (seats) has no database {@code CHECK} for
 * positivity — same choice already made for {@code OrderItem.quantity}
 * (validated only at the DTO layer via {@code @Positive}, see that
 * entity's javadoc): a plain-value business rule, not a closed enum like
 * {@code CommandStatus}, so it doesn't get the {@code CHECK}-constraint
 * treatment those get.
 *
 * <p>Id generation follows the same strategy as {@link
 * com.farelo.api.catalog.Category} — see its javadoc for why {@code
 * RANDOM} (UUIDv4) is used instead of UUIDv7.
 */
@Entity
@Table(name = "dining_table")
public class DiningTable {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "number", nullable = false, unique = true)
    private int number;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DiningTable() {
        // required by JPA
    }

    public DiningTable(int number, int capacity) {
        this.number = number;
        this.capacity = capacity;
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

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
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
        if (!(o instanceof DiningTable other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
