package com.farelo.api.ordering;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * A customer's order within a {@link Command} (comanda). Part of the
 * {@code ordering} domain (see docs/domain-model.md).
 *
 * <p>Mapped to table {@code orders} (plural), not {@code order} —
 * {@code ORDER} is a reserved SQL keyword (used in {@code ORDER BY}), so an
 * unquoted {@code order} table name would break every raw SQL statement
 * that touches it (and every future migration referencing it, e.g.
 * {@code OrderItem}'s FK in FARELO-051). The migration file is still named
 * {@code V7__create_order_table.sql} per the ticket, but it creates the
 * {@code orders} table — see that file's comment.
 *
 * <p>Scope note (FARELO-050): no {@code OrderItem} yet (FARELO-051), no
 * price snapshot (FARELO-052), no endpoints (FARELO-053+).
 *
 * <p>Id generation follows the same strategy as {@link
 * com.farelo.api.catalog.Category} — see its javadoc for why {@code RANDOM}
 * (UUIDv4) is used instead of UUIDv7.
 *
 * <p><strong>{@code customerName}/{@code customerPhone}</strong>: a plain
 * snapshot of the customer contact info collected by the QR checkout form
 * (apps/web, FARELO-045) — captured once at order-creation time, same
 * spirit as {@link OrderItem#getUnitPrice()}'s price snapshot. This is
 * deliberately <em>not</em> a {@code customer} domain/entity (see
 * docs/domain-model.md) — there's no customer account, loyalty, or
 * lookup-by-phone use case yet to justify one; both fields are nullable
 * because not every order-creation flow necessarily has this data (e.g. a
 * future POS-direct order flow that skips the QR checkout form). No
 * setters, only getters — like {@code createdAt}, this is written once at
 * construction and never changed afterward (no endpoint edits an order's
 * customer info after creation).
 *
 * <h2>Design decision — {@code BALCAO} (FARELO-312)</h2>
 *
 * {@code command} was a required {@code @ManyToOne} from FARELO-050 until
 * this ticket — every order belonged to a comanda. A {@code BALCAO} order
 * (a walk-up counter sale, docs/gaps-sischef-pdv.md item 2) has none, so
 * {@code command} became optional and {@link #getType()} says which shape
 * applies: {@code COMANDA} always has a {@code command}, {@code BALCAO}
 * never does — see {@link OrderType}'s javadoc for the full reasoning and
 * the database-level constraint that enforces it.
 * {@link OrderService#create} (unchanged) always builds a {@code COMANDA}
 * order; the new {@link OrderService#createBalcao} builds a {@code BALCAO}
 * one via {@link #Order(String, String)}.
 *
 * <p><b>Known follow-up, not solved here</b>: nothing in {@code payment}
 * can charge a {@code BALCAO} order — every payment endpoint keys off a
 * comanda number ({@code PaymentService#record(int, ...)}), and a
 * {@code BALCAO} order has none. This ticket only models the order type and
 * its creation path, per its own stated scope
 * ("adicionar tipo de pedido BALCÃO... como alternativa ao fluxo de
 * comanda existente") — a real counter-sale checkout needs a
 * {@code payment} path keyed by order id instead of commandNumber, which
 * is a distinct, future ticket, not guessed at here.
 *
 * <h2>Design decision — assigned waiter (FARELO-314)</h2>
 *
 * {@code assignedUserId}/{@code assignedUserName} name the {@code
 * com.farelo.api.security.User} currently responsible for this order — the
 * "Garçom Principal" gap (docs/gaps-sischef-pdv.md item 4). Same
 * denormalized id+name pair convention as {@link
 * OrderItem#getCancelledByUserId()}/{@link OrderItem#getCancelledByUserName()}
 * (survives a later rename/deactivation of that user), but unlike that
 * pair — a one-way historical fact — this one is genuinely mutable:
 * "removível/trocável" per the gap analysis, so {@link
 * #assignWaiter(UUID, String)}/{@link #unassignWaiter()} can be called any
 * number of times over the order's life, unlike {@link OrderItem#cancel}'s
 * single, terminal write.
 *
 * <h2>Design decision — {@code note} (FARELO-316)</h2>
 *
 * A free-text "Observação do pedido" (docs/gaps-sischef-pdv.md item 6) —
 * order-level, not item-level (unlike {@link OrderItem#getCancelDescription()},
 * which explains one specific cancelled line). Same immutable-snapshot
 * shape as {@code customerName}/{@code customerPhone}: set once at
 * creation (only getter, no setter) — Sischef's own UI collects it at "o
 * passo final de lançamento", the same moment those two fields are
 * collected, and no edit affordance was asked for.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // Optional as of FARELO-312 — null for a BALCAO order. See class
    // javadoc, "Design decision — BALCAO".
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "command_id", nullable = true)
    private Command command;

    // FARELO-312: COMANDA for every order created via the constructors
    // below that take a Command, BALCAO for the counter-sale one. See
    // OrderType's javadoc.
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private OrderType type = OrderType.COMANDA;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status = OrderStatus.CREATED;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_phone")
    private String customerPhone;

    // FARELO-314: nullable — no waiter assigned (yet, or anymore) is the
    // default and a valid permanent state. See class javadoc, "Design
    // decision — assigned waiter".
    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    @Column(name = "assigned_user_name")
    private String assignedUserName;

    // FARELO-316: nullable, no setter — see class javadoc, "Design
    // decision — note".
    @Column(name = "note")
    private String note;

    // FARELO-319 ("Imprimir ao confirmar", docs/gaps-sischef-pdv.md item
    // 9): defaults true — every order prints automatically on creation
    // (FARELO-072's existing OrderCreated -> PrintJobService#createForOrder
    // wiring) unless explicitly turned off. See PrintJobService#
    // createForOrderIfEnabled's javadoc for exactly how this is honored.
    @Column(name = "print_on_confirm", nullable = false)
    private boolean printOnConfirm = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Order() {
        // required by JPA
    }

    public Order(Command command) {
        this(command, null, null, null);
    }

    public Order(Command command, String customerName, String customerPhone) {
        this(command, customerName, customerPhone, null);
    }

    // FARELO-316: adds `note` — see class javadoc, "Design decision —
    // note". The 3-arg overload above stays for every caller (mostly test
    // fixtures) that has no note to set. Defaults printOnConfirm to true
    // (FARELO-319's own default) for every caller that doesn't care.
    public Order(Command command, String customerName, String customerPhone, String note) {
        this(command, customerName, customerPhone, note, true);
    }

    // FARELO-319: adds `printOnConfirm` — see class javadoc, "Design
    // decision — note" (same immediate-neighbor field, same reasoning
    // applies: set once at creation).
    public Order(
            Command command, String customerName, String customerPhone, String note, boolean printOnConfirm) {
        this.command = command;
        this.type = OrderType.COMANDA;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.note = note;
        this.printOnConfirm = printOnConfirm;
    }

    // FARELO-312: the BALCAO counterpart — no Command at all. See class
    // javadoc, "Design decision — BALCAO". Defaults printOnConfirm to
    // true, same reasoning as the 4-arg COMANDA overload above.
    public Order(String customerName, String customerPhone, String note) {
        this(customerName, customerPhone, note, true);
    }

    // FARELO-319: the BALCAO counterpart of the 5-arg COMANDA overload
    // above.
    public Order(String customerName, String customerPhone, String note, boolean printOnConfirm) {
        this.command = null;
        this.type = OrderType.BALCAO;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.note = note;
        this.printOnConfirm = printOnConfirm;
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

    public Command getCommand() {
        return command;
    }

    public void setCommand(Command command) {
        this.command = command;
    }

    public OrderType getType() {
        return type;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public String getNote() {
        return note;
    }

    public boolean isPrintOnConfirm() {
        return printOnConfirm;
    }

    public UUID getAssignedUserId() {
        return assignedUserId;
    }

    public String getAssignedUserName() {
        return assignedUserName;
    }

    /**
     * Assigns (or reassigns) the waiter/attendant responsible for this
     * order (FARELO-314). The one writer of both fields together — same
     * "never end up with an id but no name, or vice versa" reasoning
     * {@link OrderItem#cancel} already established for its own denormalized
     * actor pair.
     */
    public void assignWaiter(UUID userId, String userName) {
        this.assignedUserId = userId;
        this.assignedUserName = userName;
    }

    /**
     * Clears the assignment — "removível" per the gap analysis (docs/
     * gaps-sischef-pdv.md item 4). Distinct from calling {@link
     * #assignWaiter(UUID, String)} with a null id: this method exists so
     * the intent ("no one is responsible for this order right now") reads
     * clearly at the call site instead of a slightly odd {@code
     * assignWaiter(null, null)}.
     */
    public void unassignWaiter() {
        this.assignedUserId = null;
        this.assignedUserName = null;
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
        if (!(o instanceof Order other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
