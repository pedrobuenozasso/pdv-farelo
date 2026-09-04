package com.farelo.api.fiscal;

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
 * A durable record representing a fiscal document (an NFC-e, once Epic 12
 * eventually emits real ones) associated with a comanda/sale. Third entity
 * of the {@code fiscal} domain (see docs/domain-model.md), FARELO-156 —
 * follows the same "entity first, real producers/emission logic later"
 * pattern already used by {@code PrintJob} (FARELO-071), {@code
 * Notification} (FARELO-110), {@code InventoryMovement} (FARELO-093),
 * {@code AuditLog} (FARELO-125), {@code Payment} (FARELO-140) and {@code
 * FiscalProfile} (FARELO-150) at their own first tickets.
 *
 * <h2>Critical scoping boundary</h2>
 *
 * Epic 11 ("Fiscal Base", prompt mestre seção 47) is explicit: "NÃO EMITIR
 * NFC-e ainda." Epic 12 ("NFC-e", FARELO-170-178) is the epic that actually
 * emits real documents (XML generation, digital signature, SEFAZ
 * transmission, authorization/rejection handling) and is explicitly gated —
 * "Somente iniciar após validação contábil" — meaning it is not started by
 * this ticket. This class is therefore a plain data-holding entity: no
 * emission logic, no SEFAZ client, no XML/signature code, no state machine
 * driving toward "transmitted". Every identifying field a real NFC-e
 * eventually needs (document number/série, chave de acesso, protocol
 * number, XML content, authorization date) is modeled here only as a
 * nullable placeholder — nothing computes, validates, or transmits any of
 * them yet. Same relationship {@code PrintJob} (FARELO-071) had to {@code
 * PENDING}/{@code PRINTED}/{@code FAILED} before FARELO-072-079 built any of
 * the actual printing/retry logic.
 *
 * <h2>Relationship to {@code Command}, not {@code Order}: unidirectional
 * {@code @ManyToOne}, mirroring {@code Payment.command}</h2>
 *
 * Prompt mestre seção 25 models the NFC-e flow as {@code Close Command →
 * Payment → Fiscal Service → NFC-e → SEFAZ → AUTHORIZED} — the trigger for
 * emitting a fiscal document is closing the comanda (settling the whole
 * tab), the exact same billable unit {@link
 * com.farelo.api.payment.Payment#getCommand()} (FARELO-140) already settled
 * on over {@code Order}, and for the same reason: seção 30's transaction
 * example is about placing one order, but "fechar a conta"/emit a fiscal
 * document is a comanda-level event, not a per-order one. A customer's
 * comanda may carry several orders (several trips to the counter) but is
 * billed, paid, and — eventually — fiscally documented as one unit. {@code
 * command} is therefore a required {@code @ManyToOne}, the same shape as
 * {@code Payment.command}/{@code Order.command}. No {@code @OneToMany} back
 * from {@code Command} — same reasoning {@code Payment}'s javadoc already
 * gives in full: nothing here needs a collection-management surface
 * (cascade rules, orphan removal, lazy-collection footguns); a future
 * "does this comanda have a fiscal document" query reads through {@link
 * FiscalDocumentRepository} instead, keyed by {@code command}.
 *
 * <h2>{@code status}</h2>
 *
 * {@link FiscalDocumentStatus} — the literal six-value vocabulary from
 * prompt mestre seção 25, defaulting to {@code PENDING} ("not yet
 * emitted"). See that enum's javadoc for the full sourcing/scope reasoning,
 * including why no transition logic accompanies it here (that is
 * FARELO-157, "Criar estados fiscais", a separate, later-numbered ticket in
 * this same epic).
 *
 * <h2>Identifying fields — nullable placeholders only</h2>
 *
 * {@code documentNumber}/{@code series}, {@code accessKey} (the 44-digit
 * "chave de acesso"), {@code protocolNumber}, {@code xmlContent} and {@code
 * authorizedAt} are exactly the fields prompt mestre seções 24-25 anticipate
 * a real NFC-e eventually carrying, once Epic 12 emits one — every one of
 * them is nullable and unpopulated by this ticket, since nothing here
 * computes/validates/transmits any of them. {@code accessKey} is modeled as
 * a plain, unvalidated {@code String} (no 44-digit-format check) — same
 * contained-inference discipline already applied to {@code
 * CompanyFiscalConfiguration.cnpj} (no digit-verification logic, since the
 * prompt mestre doesn't define one and inventing it would be scope creep
 * beyond this ticket).
 *
 * <p><strong>Deliberately mutable, unlike {@code Payment}</strong>: {@code
 * Payment} is an append-only ledger because a *recorded* payment is already
 * a completed fact the instant it's written (see that class's javadoc). A
 * {@code FiscalDocument} is the opposite: it starts life as a placeholder
 * ({@code PENDING}, every identifying field {@code null}) that a *future*
 * emission process (Epic 12) will fill in and drive through {@code
 * status} over time. So, unlike {@code Payment}, columns here are not
 * {@code updatable = false} and this class does carry an {@code updatedAt}
 * (same {@code createdAt}/{@code updatedAt} + {@code @PreUpdate} shape as
 * {@code PrintJob}, another status-carrying entity with a genuine future
 * lifecycle) — but see {@link #setStatus(FiscalDocumentStatus)}'s javadoc
 * for why the mutators themselves stay deliberately dumb.
 *
 * <p>Id generation follows the same strategy as every other entity in this
 * codebase — Hibernate 6.6's {@code @UuidGenerator} has no native UUIDv7
 * support, so {@code RANDOM} (UUIDv4) is used.
 */
@Entity
@Table(name = "fiscal_document")
public class FiscalDocument {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "command_id", nullable = false, updatable = false)
    private Command command;

    // EnumType.STRING, never ORDINAL — see FiscalDocumentStatus's javadoc.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FiscalDocumentStatus status = FiscalDocumentStatus.PENDING;

    // Nullable placeholders only — see class javadoc, "Identifying fields".
    // None of these are populated by this ticket; a future FARELO-170+
    // producer fills them in once real emission exists.
    @Column(name = "document_number")
    private Integer documentNumber;

    @Column(name = "series")
    private Integer series;

    @Column(name = "access_key", length = 44)
    private String accessKey;

    @Column(name = "protocol_number", length = 64)
    private String protocolNumber;

    @Column(name = "xml_content", columnDefinition = "TEXT")
    private String xmlContent;

    @Column(name = "authorized_at")
    private OffsetDateTime authorizedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected FiscalDocument() {
        // required by JPA
    }

    /**
     * Creates a new fiscal document placeholder for {@code command}, status
     * {@code PENDING}, every identifying field {@code null}. No other
     * constructor overload exists — a real emission process (Epic 12) is
     * expected to fill in the rest via the setters below, over time, as it
     * progresses the document through {@link FiscalDocumentStatus}.
     */
    public FiscalDocument(Command command) {
        this.command = command;
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

    public FiscalDocumentStatus getStatus() {
        return status;
    }

    /**
     * Plain, unvalidated status mutator — deliberately no domain-named
     * transition methods (e.g. {@code markAuthorized()}/{@code
     * markRejected()}) and no legality checks on the given {@code status}
     * (e.g. rejecting a move out of a terminal state). Same "entity holds
     * the field, a future ticket decides the rules" split {@code
     * Command#setStatus}/{@code CommandStatus} already establishes — and,
     * per this ticket's own scope boundary, exactly the kind of
     * state-machine logic FARELO-157 ("Criar estados fiscais") is the
     * named, separate ticket for. This ticket only needs the field to
     * exist and be settable; it does not need to guess FARELO-157's rules.
     */
    public void setStatus(FiscalDocumentStatus status) {
        this.status = status;
    }

    public Integer getDocumentNumber() {
        return documentNumber;
    }

    public void setDocumentNumber(Integer documentNumber) {
        this.documentNumber = documentNumber;
    }

    public Integer getSeries() {
        return series;
    }

    public void setSeries(Integer series) {
        this.series = series;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getProtocolNumber() {
        return protocolNumber;
    }

    public void setProtocolNumber(String protocolNumber) {
        this.protocolNumber = protocolNumber;
    }

    public String getXmlContent() {
        return xmlContent;
    }

    public void setXmlContent(String xmlContent) {
        this.xmlContent = xmlContent;
    }

    public OffsetDateTime getAuthorizedAt() {
        return authorizedAt;
    }

    public void setAuthorizedAt(OffsetDateTime authorizedAt) {
        this.authorizedAt = authorizedAt;
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
        if (!(o instanceof FiscalDocument other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
