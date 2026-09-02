package com.farelo.api.security;

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
 * A person who can operate the system (a Farelo employee) — the account
 * record itself, not a login session. First entity of the {@code security}
 * domain (see docs/domain-model.md).
 *
 * <p><b>FARELO-120 scope</b>: only this entity and its CRUD. No
 * authentication (login, password verification, tokens/sessions —
 * FARELO-121), no RBAC enforcement (FARELO-122), no protected endpoints
 * (FARELO-123/124) — see docs/PROMPT_MESTRE.md seção 26/EPIC 9 and
 * docs/domain-model.md for the full reasoning behind that split.
 *
 * <p><b>{@code passwordHash}</b>: always a BCrypt hash, never plaintext.
 * {@link UserService} is the only writer of this field and always hashes a
 * raw password (via the {@code PasswordEncoder} bean, see
 * {@link PasswordEncoderConfig}'s javadoc for the dependency decision)
 * before ever calling {@link #setPasswordHash(String)} — the entity itself
 * has no opinion on hashing, same "dumb mutator" role as every other setter
 * on every other entity in this codebase. Never logged, never returned by
 * any API response (see {@code UserResponse} in the {@code web} subpackage)
 * — enforced at the DTO boundary, since the entity is never serialized
 * directly (AGENTS.md).
 *
 * <p><b>{@code email}</b>: required and unique (backed by {@code
 * uk_app_user_email} in {@code V20__create_user_table.sql}) — it will be the
 * login identifier once FARELO-121 exists, even though the login mechanism
 * itself doesn't exist yet.
 *
 * <p><b>{@code role}</b>: see {@link UserRole}'s javadoc for why this field
 * is included now as schema preparation, despite RBAC (FARELO-122) not
 * existing yet.
 *
 * <p><b>{@code active}</b> (default {@code true}): same pattern as {@code
 * Category}/{@code Product}/{@code Printer}/{@code Ingredient} — lets a
 * user be deactivated (e.g. an employee who left) without deleting their
 * historical record.
 *
 * <p>Table name {@code app_user}, not {@code user}: {@code USER} is a
 * reserved keyword in SQL (and a Postgres built-in resolving to {@code
 * CURRENT_USER}) — same reasoning already applied to the {@code orders}
 * table (not {@code order}) in the {@code ordering} domain, see
 * docs/domain-model.md.
 *
 * <p>Id generation: same strategy as every other domain — Hibernate 6.6's
 * {@code @UuidGenerator} only supports {@code AUTO}, {@code RANDOM} and
 * {@code TIME} styles (no native UUIDv7 without an external library), so
 * {@code RANDOM} (UUIDv4) is used.
 */
@Entity
@Table(name = "app_user")
public class User {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected User() {
        // required by JPA
    }

    public User(String name, String email, String passwordHash, UserRole role) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
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
        if (!(o instanceof User other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
