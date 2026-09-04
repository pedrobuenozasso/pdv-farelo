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
 * A reusable fiscal/tax classification (e.g. "Isento", "Tributado padrão")
 * that a {@code Product} will later be associated with (FARELO-151, a
 * future ticket — not this one), so multiple products sharing the same tax
 * treatment don't each need to repeat those attributes individually. First
 * entity of the {@code fiscal} domain (prompt mestre seção 23, Epic 11 —
 * see {@code docs/PROMPT_MESTRE.md} seção 47) — same "entity first,
 * associations/producers later" pattern already used by {@code PrintJob}
 * (FARELO-071), {@code Notification} (FARELO-110), {@code
 * InventoryMovement} (FARELO-093), {@code AuditLog} (FARELO-125) and
 * {@code Payment} (FARELO-140) in their own first tickets.
 *
 * <p><b>FARELO-150 scope — deliberately no NCM/CFOP/CST/CSOSN (or any other
 * fiscal code) yet.</b> Prompt mestre seção 24 ("Dados fiscais previstos")
 * lists NCM, CFOP, CST/CSOSN, CEST, origem, cBenef, ICMS, CBS, IBS as
 * attributes a fiscal profile should <i>eventually</i> support — but the
 * roadmap (seção 47, Epic 11) names three of those as their own explicit,
 * later-numbered tickets in this exact sequence: FARELO-152 ("Adicionar
 * NCM"), FARELO-153 ("Adicionar CFOP"), FARELO-154 ("Adicionar
 * CST/CSOSN"). That ordering is a deliberate signal, the same "don't
 * anticipate a future ticket's field" discipline this codebase has applied
 * repeatedly (e.g. {@code Ingredient} not getting {@code minimumStock}
 * until FARELO-099, {@code criticalStock} still not existing anywhere; see
 * also {@code Recipe}/{@code RecipeItem}'s own header-then-detail split,
 * FARELO-091/092). CEST/origem/cBenef/ICMS/CBS/IBS aren't even named as
 * tickets yet, so they're further out of scope still. Building any of
 * these into FARELO-150 would both violate that discipline and preempt
 * three concrete future tickets whose entire reason to exist is adding
 * exactly these fields one at a time.
 *
 * <p><b>FARELO-152 ("Adicionar NCM") update — {@link #ncm} added.</b> The
 * first of the three fiscal codes named above, now implemented. NCM
 * (Nomenclatura Comum do Mercosul) is a real, standardized Brazilian tax
 * classification code, always exactly 8 numeric digits — that shape is
 * fixed by Brazilian tax law, not an app-specific format choice. Nullable
 * (see {@link #ncm}'s own javadoc) — CFOP/CST/CSOSN remain out of scope for
 * this ticket, same discipline as above (FARELO-153/154 still not
 * implemented).
 *
 * <p><b>FARELO-153 ("Adicionar CFOP") update — {@link #cfop} added.</b> The
 * second of the three fiscal codes named above, same "one field per ticket"
 * shape as FARELO-152. CFOP (Código Fiscal de Operações e Prestações) is a
 * real, standardized Brazilian tax code classifying the nature of a fiscal
 * operation (e.g. sale within-state vs. out-of-state), always exactly 4
 * numeric digits — that shape is fixed by Brazilian tax law, not an
 * app-specific format choice. Nullable (see {@link #cfop}'s own javadoc) —
 * CST/CSOSN remains out of scope for this ticket, same discipline as above
 * (FARELO-154 still not implemented).
 *
 * <p>What a {@code FiscalProfile} minimally needs to exist as a real,
 * useful row <i>before</i> any fiscal code is attached to it is just a way
 * for a business owner to name and describe a fiscal category they intend
 * to assign codes to later (e.g. "Isento", "Tributado padrão", "Zona
 * Franca de Manaus") — so this ticket adds only {@link #name} (required)
 * and {@link #description} (optional, free text — e.g. "Produtos sem
 * incidência de ICMS", explaining when to use this profile), plus the
 * {@link #active}/{@code createdAt}/{@code updatedAt} bookkeeping fields
 * every other entity in this codebase already carries. This mirrors {@code
 * Category} (FARELO-010) almost exactly — both are simple named lookup
 * values a {@code Product} will reference — with {@code description} added
 * on top because, unlike a menu category ("Bebidas", self-explanatory), a
 * fiscal profile's name alone ("Tributado padrão") often isn't enough for
 * whoever configures it later to remember which real-world products belong
 * under it; same optional-description shape as {@code Product.description}.
 *
 * <p>Id generation: same strategy as {@code Category}/{@code Product}/
 * {@code Ingredient} — Hibernate 6.6's {@code @UuidGenerator} only supports
 * {@code AUTO}, {@code RANDOM} and {@code TIME} styles, no native UUIDv7
 * without an external library, so {@code RANDOM} (UUIDv4) is used.
 */
@Entity
@Table(name = "fiscal_profile")
public class FiscalProfile {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    /**
     * NCM (Nomenclatura Comum do Mercosul), FARELO-152 — a real,
     * standardized Brazilian tax classification code, always exactly 8
     * numeric digits. Nullable: existing {@code FiscalProfile} rows predate
     * this ticket and have no NCM, and there is no meaningful "default NCM"
     * to backfill (unlike {@link #active}, whose default is genuinely
     * universal) — same "null means not configured yet, a distinct,
     * permanent state" reasoning as {@code Ingredient.minimumStock}
     * (FARELO-099). Format ({@code ^[0-9]{8}$}) is validated at the request
     * DTO boundary ({@code @Pattern} on {@code FiscalProfileRequest}/{@code
     * FiscalProfileUpdateRequest}) and backstopped by a DB {@code CHECK}
     * constraint (see {@code V30__add_fiscal_profile_ncm_column.sql}) for
     * any write path that bypasses the DTO — same defense-in-depth pattern
     * as {@code Ingredient.minimumStock}'s {@code CHECK (minimum_stock IS
     * NULL OR minimum_stock >= 0)}.
     */
    @Column(name = "ncm", length = 8)
    private String ncm;

    /**
     * CFOP (Código Fiscal de Operações e Prestações), FARELO-153 — a real,
     * standardized Brazilian tax code classifying the nature of a fiscal
     * operation (e.g. sale within-state vs. out-of-state), always exactly 4
     * numeric digits. Nullable: existing {@code FiscalProfile} rows predate
     * this ticket and have no CFOP, and there is no meaningful "default
     * CFOP" to backfill (unlike {@link #active}, whose default is genuinely
     * universal) — same "null means not configured yet, a distinct,
     * permanent state" reasoning as {@link #ncm} (FARELO-152). Format
     * ({@code ^[0-9]{4}$}) is validated at the request DTO boundary
     * ({@code @Pattern} on {@code FiscalProfileRequest}/{@code
     * FiscalProfileUpdateRequest}) and backstopped by a DB {@code CHECK}
     * constraint (see {@code V32__add_fiscal_profile_cfop_column.sql}) for
     * any write path that bypasses the DTO — same defense-in-depth pattern
     * as {@link #ncm}'s {@code CHECK (ncm IS NULL OR ncm ~ '^[0-9]{8}$')}.
     */
    @Column(name = "cfop", length = 4)
    private String cfop;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected FiscalProfile() {
        // required by JPA
    }

    public FiscalProfile(String name) {
        this.name = name;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getNcm() {
        return ncm;
    }

    public void setNcm(String ncm) {
        this.ncm = ncm;
    }

    public String getCfop() {
        return cfop;
    }

    public void setCfop(String cfop) {
        this.cfop = cfop;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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
        if (!(o instanceof FiscalProfile other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
