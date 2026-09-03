package com.farelo.api.payment;

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
 * A payment recorded against a {@link Command} (comanda) — not against an
 * individual {@code Order}: prompt mestre seção 30 models the whole
 * transaction unit as the comanda ("criar pedido... registrar evento
 * outbox... COMMIT" happens per order, but settling the tab is a
 * comanda-level concern), and Epic 10's own roadmap confirms it — FARELO-142
 * ("Permitir múltiplos pagamentos por comanda") and FARELO-143 ("Validar
 * total pago antes de fechar", i.e. before {@code Command} transitions to
 * {@code CLOSED}) only make sense if payments are summed per comanda, not
 * per order. First entity of the {@code payment} domain (see
 * docs/domain-model.md), FARELO-140, following the same "entity first,
 * producers/endpoints later" pattern already used by {@code PrintJob}
 * (FARELO-071), {@code Notification} (FARELO-110), {@code InventoryMovement}
 * (FARELO-093) and {@code AuditLog} (FARELO-125) at their own first tickets.
 *
 * <h2>Relationship to {@code Command}: unidirectional {@code @ManyToOne},
 * mirroring {@code Order.command}</h2>
 *
 * {@code command} is a required {@code @ManyToOne}, the exact same shape as
 * {@link com.farelo.api.ordering.Order#getCommand()} — a comanda can have
 * many payments (FARELO-142), every payment belongs to exactly one comanda.
 * {@code Command} itself gets no {@code @OneToMany} back to {@code Payment}
 * (checked: {@code Order}'s own required {@code @ManyToOne} to {@code
 * Command} needed no matching change inside {@code Command} either — the
 * relationship is unidirectional there too). A future "how much has been
 * paid for this comanda" computation (FARELO-142/143) reads through {@code
 * PaymentRepository} (a derived/aggregate query keyed by {@code command}),
 * the same way {@code InventoryMovementRepository#sumQuantityByIngredientId}
 * already answers "what's this ingredient's balance" without {@code
 * Ingredient} owning a collection of its own movements. A bidirectional
 * {@code @OneToMany} would buy nothing over that and would add exactly the
 * kind of collection-management surface (cascade rules, orphan removal,
 * lazy-collection footguns) this codebase has consistently avoided for
 * every other ledger-shaped child entity ({@code OrderItem}, {@code
 * InventoryMovement}, {@code PrintJob}, {@code AuditLog} — none of their
 * parents carry a matching collection either).
 *
 * <h2>Design decision — no status field; append-only, like
 * {@code InventoryMovement}/{@code AuditLog}</h2>
 *
 * Prompt mestre seção 30 gives no "pending"/"confirmed" vocabulary for a
 * payment — the transaction example there is entirely about placing an
 * <em>order</em>, not settling a tab, and nothing elsewhere in the document
 * describes a payment as going through states. FARELO-141 ("Registrar
 * pagamento manual") is explicitly a human recording, after the fact, that
 * cash/card/PIX was received — not an async gateway callback that starts
 * "pending" and later resolves to "confirmed" or "failed" days apart. The
 * instant a manual payment is recorded, it already is a completed fact: the
 * money changed hands (or was decided to have) before anyone types it into
 * the system. That's exactly {@code InventoryMovement}'s/{@code AuditLog}'s
 * situation, not, say, {@code PrintJob}'s (which genuinely has an async
 * "sent to a physical device, may fail" lifecycle worth a {@code status}
 * for). So {@code Payment} follows the ledger shape instead: every column
 * is {@code updatable = false}, no setters exist, and there is no
 * repository {@code update}/{@code delete} usage anywhere in this domain —
 * only {@code save} for a new row and read queries (see {@link
 * PaymentRepository}). If a recorded payment turns out to be wrong (wrong
 * amount, wrong method, duplicate entry), the fix is a new *offsetting*
 * record, never an edit to the original — same reasoning already
 * established for {@code InventoryMovement}'s javadoc, and the same reason
 * this class has {@code createdAt} only, no {@code updatedAt}: an
 * {@code updatedAt} that could only ever equal {@code createdAt} would
 * imply a row *could* be revised in place, which is exactly the anti-pattern
 * an append-only financial ledger exists to avoid. Should a real refund/
 * void use case appear later, it is itself a new fact ("this payment was
 * reversed"), not a mutation of the original row — a future ticket's
 * decision (plausibly a {@code REFUND}-shaped {@code Payment}, or a
 * dedicated field/entity; this ticket does not need to guess which).
 *
 * <p><b>Escopo deste ticket é só a entidade {@code Payment} em si.</b>
 * Nothing in this codebase constructs a real {@code Payment} yet
 * (FARELO-141), nothing sums payments per comanda (FARELO-142), and nothing
 * validates a comanda's total paid before closing it (FARELO-143) — all
 * future, separately-numbered tickets. This ticket does not touch {@code
 * CommandService}/{@code Command} at all.
 *
 * <p>Id generation: same strategy as every other entity in this codebase —
 * Hibernate 6.6's {@code @UuidGenerator} has no native UUIDv7 support, so
 * {@code RANDOM} (UUIDv4) is used.
 */
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "command_id", nullable = false, updatable = false)
    private Command command;

    // BigDecimal / NUMERIC(10,2), never double/float — same money
    // convention as Product.price/OrderItem.unitPrice (AGENTS.md).
    @Column(name = "amount", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, updatable = false)
    private PaymentMethod method;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Payment() {
        // required by JPA
    }

    public Payment(Command command, BigDecimal amount, PaymentMethod method) {
        this.command = command;
        this.amount = amount;
        this.method = method;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Payment other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
