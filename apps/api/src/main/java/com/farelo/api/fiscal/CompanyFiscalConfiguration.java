package com.farelo.api.fiscal;

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
 * The business's OWN fiscal identity/settings — the company that will one
 * day issue NFC-e — as opposed to {@link FiscalProfile}, which classifies
 * individual <i>products</i> ("Isento", "Tributado padrão"). Second entity
 * of the {@code fiscal} domain (prompt mestre seção 23, Epic 11 — see
 * {@code docs/PROMPT_MESTRE.md} seção 47: "NÃO EMITIR NFC-e ainda"), and,
 * unlike {@code FiscalProfile}, entirely independent from it: no FK between
 * the two, {@code Product} keeps pointing at {@code FiscalProfile} only.
 *
 * <p><b>Field sourcing — what's from prompt mestre text vs. inferred.</b>
 * Unlike {@code FiscalProfile} (whose {@code name}/{@code description} also
 * weren't literal prompt-mestre text, but whose <i>omitted</i> fields —
 * NCM/CFOP/CST/CSOSN — are each a literal, later-numbered roadmap ticket:
 * FARELO-152/153/154), prompt mestre seção 24 ("Dados fiscais previstos")
 * gives NO field list for company-level fiscal data at all — its list
 * (NCM, CFOP, CST/CSOSN, CEST, origem, cBenef, ICMS, CBS, IBS) is entirely
 * per-<i>product</i> tax-classification data, {@code FiscalProfile}'s
 * territory. Seção 23 only names this entity ({@code
 * CompanyFiscalConfiguration}) as one of five anticipated fiscal entities —
 * it does not enumerate its columns. So, unlike {@code FiscalProfile}'s
 * omissions (each backed by a concrete future ticket number), there is no
 * roadmap ticket anywhere that reads "Adicionar CNPJ à
 * CompanyFiscalConfiguration" to defer to — leaving this entity with zero
 * business fields would make it permanently empty with no ticket ever
 * meant to fill it in, defeating the one purpose seção 23 assigns it.
 *
 * <p>The fields below are therefore inferred from this entity's own stated
 * purpose, not lifted from prompt-mestre text — same inference method
 * already used for {@code FiscalProfile.name}/{@code description}, applied
 * here to "what a Brazilian business is minimally identified by":
 * <ul>
 *   <li>{@link #cnpj} and {@link #legalName} (required) — the bare minimum
 *   for this row to answer "which company is this". A CNPJ is the
 *   government-issued identifier for the legal entity; without at least
 *   these two, the row carries no fiscal identity at all.</li>
 *   <li>{@link #tradeName}, {@link #stateRegistration}, {@link #address}
 *   (optional) — standard company-registration identity data (trade name,
 *   inscrição estadual, address) that fills out "who/where this company
 *   is" beyond the bare CNPJ/legal name, mirroring how {@code
 *   FiscalProfile.description} fills out a bare {@code name}. {@code
 *   address} is a single free-text field (same nullable-{@code TEXT} shape
 *   as {@code Product.description}/{@code FiscalProfile.description}), not
 *   a structured multi-column address — no consumer needs a structured
 *   address yet (real NFC-e XML emission is Epic 12, gated on accounting
 *   validation, and explicitly out of scope for FARELO-155). {@code
 *   stateRegistration} is nullable rather than required because some
 *   businesses are legitimately IE-isento.</li>
 * </ul>
 *
 * <p><b>Deliberately NO tax-regime/environment field</b> (e.g. Simples
 * Nacional vs. Regime Normal). This is the one field this ticket's own
 * brief flagged as plausible but explicitly told to verify before adding —
 * and prompt mestre never names "Simples Nacional", "Regime Normal", or any
 * regime/CRT concept anywhere. Unlike CNPJ/legal name (universal, purely
 * identifying data — "what is this company"), a tax regime indicator is
 * tax-<i>classification</i> data, the same category of thing as the
 * NCM/CFOP/CST fields {@code FiscalProfile} deliberately deferred — the
 * difference is those have reserved future ticket numbers and this doesn't.
 * Approximating it with a loose, unvalidated {@code String} would just be a
 * disguised version of the very enum this ticket was told not to invent
 * without basis. Left out entirely; a future ticket (once the roadmap
 * actually names one, or once Epic 12's real NFC-e emission needs it) is
 * the right place to add it — not preempted here.
 *
 * <p><b>{@code active}</b> is deliberately NOT carried over from {@code
 * FiscalProfile}: {@code active} exists there because many {@code Product}
 * rows reference a {@code FiscalProfile} by id and the business wants to
 * retire one without deleting/orphaning those references. There is nothing
 * analogous here — nothing else in the schema references {@code
 * company_fiscal_configuration} by id, and there is exactly one row, so
 * there is no "retire this one but keep the others" scenario to support.
 *
 * <p>Id generation: same strategy as {@code FiscalProfile}/{@code
 * Category}/{@code Product}/{@code Ingredient} — Hibernate 6.6's {@code
 * @UuidGenerator} only supports {@code AUTO}, {@code RANDOM} and {@code
 * TIME} styles, no native UUIDv7 without an external library, so {@code
 * RANDOM} (UUIDv4) is used — even though, unlike those entities, the id is
 * never addressed directly by a client (see {@code
 * CompanyFiscalConfigurationController}'s javadoc for the singleton
 * endpoint shape: no {@code {id}} path variable anywhere).
 */
@Entity
@Table(name = "company_fiscal_configuration")
public class CompanyFiscalConfiguration {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "cnpj", nullable = false)
    private String cnpj;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "trade_name")
    private String tradeName;

    @Column(name = "state_registration")
    private String stateRegistration;

    @Column(name = "address")
    private String address;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected CompanyFiscalConfiguration() {
        // required by JPA
    }

    public CompanyFiscalConfiguration(String cnpj, String legalName) {
        this.cnpj = cnpj;
        this.legalName = legalName;
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

    public String getCnpj() {
        return cnpj;
    }

    public void setCnpj(String cnpj) {
        this.cnpj = cnpj;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getTradeName() {
        return tradeName;
    }

    public void setTradeName(String tradeName) {
        this.tradeName = tradeName;
    }

    public String getStateRegistration() {
        return stateRegistration;
    }

    public void setStateRegistration(String stateRegistration) {
        this.stateRegistration = stateRegistration;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
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
        if (!(o instanceof CompanyFiscalConfiguration other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
