package com.farelo.api.command;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
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
