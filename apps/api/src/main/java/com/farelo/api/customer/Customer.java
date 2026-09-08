package com.farelo.api.customer;

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
 * FARELO-313: the {@code customer} domain's first entity — a registered
 * customer of the café ("Cliente" in the Sischef comparison that motivated
 * this ticket, see {@code docs/gaps-sischef-pdv.md}). Domain reserved but
 * unimplemented since FARELO-045/FARELO-050 (see the {@code customer} row
 * in {@code docs/domain-model.md}'s domain table) — this ticket is what
 * actually starts it.
 *
 * <p><b>Deliberately independent from {@link com.farelo.api.ordering.Order}
 * and {@link com.farelo.api.command.Command}'s own customer fields — not a
 * replacement for either</b>:
 * <ul>
 *   <li>{@code Order.customerName}/{@code customerPhone} stay exactly as
 *   they are: an immutable per-order snapshot captured once at checkout
 *   time, with no setters (see that class's javadoc). Nothing about this
 *   ticket touches {@code Order}, {@code OrderService}, or
 *   {@code OrderController} — see this ticket's own scope note below.</li>
 *   <li>{@code Command.customerName}/{@code customerPhone} (FARELO-190/191)
 *   stay exactly as they are too: the current, editable "who is at this
 *   comanda right now" text record, written directly by staff or via the
 *   QR checkout's write-through. This entity does <b>not</b> replace that
 *   mechanism and {@code Command} gains no reference to {@code Customer} in
 *   this ticket.</li>
 * </ul>
 *
 * <p>What {@code Customer} actually is: a standalone, searchable registry
 * — the thing neither of the above can be, because both are scoped to a
 * single order/comanda in time, not a person across visits. This is
 * exactly the trigger {@code docs/domain-model.md}'s {@code ordering}
 * section already named for when the {@code customer} domain should
 * start: "se o produto ganhar conta de cliente... histórico de pedidos por
 * cliente... que precise consultar/agregar por identidade do cliente ao
 * longo de múltiplos pedidos/comandas" — a lookup-by-phone customer
 * registry is precisely that.
 *
 * <p><b>Explicit scope decision (FARELO-313)</b>: this ticket only builds
 * the registry itself (entity + {@code POST}/{@code GET
 * /api/v1/customers}) — it deliberately does <b>not</b> wire a {@code
 * Customer} selection into {@code OrderController}/{@code OrderService} or
 * any PDV order-creation screen, and does <b>not</b> migrate/replace
 * {@code Order.customerName}/{@code customerPhone}. That integration
 * belongs to the separate FARELO-312/314/316-320 batch (already touching
 * {@code Order}/{@code OrderItem} sequentially in another workstream) —
 * bolting it on here would risk colliding with that work on the very same
 * files. The simpler, non-breaking choice (leave {@code Order}'s snapshot
 * untouched, treat {@code Customer} as an optional future source for it)
 * is also just the right one on the merits: there is no evidence yet that
 * the snapshot mechanism is wrong, only that a *lookup* capability is
 * missing.
 *
 * <p>Id generation follows the same strategy as {@link
 * com.farelo.api.catalog.Category} — see its javadoc for why {@code
 * RANDOM} (UUIDv4) is used instead of UUIDv7.
 *
 * <p>{@code phone} is optional (a customer can be registered by name only)
 * and, when present, normalized the same way as {@code
 * Command.customerPhone} (digits only, Brazilian country code prepended
 * when it looks like a bare local number) — see {@code
 * CustomerService#normalizePhone}'s javadoc for why that heuristic is
 * duplicated here rather than extracted into a shared utility.
 * Deliberately <b>not</b> enforced unique at this ticket: a strict
 * de-duplication policy (e.g. "one registration per phone number") is a
 * real product decision with its own edge cases (merging duplicates,
 * what happens to a customer with no phone at all) that this ticket's scope
 * — "criar cadastro" — doesn't ask for; the search endpoint
 * ({@code GET /api/v1/customers?query=}) already lets staff find an
 * existing record by phone/name before creating a possible duplicate,
 * without the system hard-blocking a second entry if they don't.
 */
@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone")
    private String phone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Customer() {
        // required by JPA
    }

    public Customer(String name, String phone) {
        this.name = name;
        this.phone = phone;
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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
        if (!(o instanceof Customer other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
