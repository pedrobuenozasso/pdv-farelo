package com.farelo.api.printing;

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
 * A physical printing device (e.g. a thermal printer at the bar or kitchen
 * station). First entity of the {@code printing} domain (see
 * docs/domain-model.md) — the foundation for {@code PrintJob} (FARELO-071)
 * and, later, per-{@code productionStation} routing (FARELO-073/074).
 *
 * <p>Deliberately minimal for now: no {@code productionStation} here — that
 * belongs to {@code Product} (FARELO-073), not to the printer itself.
 *
 * <p>Id generation: same strategy as {@code Category}/{@code Product}/
 * {@code Command} — Hibernate 6.6's {@code @UuidGenerator} only supports
 * {@code AUTO}, {@code RANDOM} and {@code TIME} styles, no native UUIDv7
 * without an external library, so {@code RANDOM} (UUIDv4) is used.
 */
@Entity
@Table(name = "printer")
public class Printer {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Printer() {
        // required by JPA
    }

    public Printer(String name) {
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
        if (!(o instanceof Printer other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
