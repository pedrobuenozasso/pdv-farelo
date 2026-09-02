-- V20__create_user_table.sql
-- Security/Admin domain (FARELO-120): first table of the security domain —
-- the account of a person who can operate the system (a Farelo employee).
-- Only the account record itself. No login mechanism exists yet
-- (FARELO-121): password_hash is stored (BCrypt, never plaintext) in
-- preparation for it, but nothing in this ticket authenticates against it —
-- see docs/domain-model.md for the full reasoning.
--
-- Table name app_user, not user: USER is a reserved keyword in SQL (also a
-- Postgres built-in that resolves to CURRENT_USER) — same reasoning already
-- applied to `orders` vs `order` in the ordering domain (see V7 migration /
-- docs/domain-model.md).
--
-- role uses the same VARCHAR + CHECK constraint convention as
-- ingredient.unit (V16) / product.production_station (V14) / command.status
-- (V5), mirroring the five profiles literally named by the prompt mestre
-- seção 26 (ADMIN/MANAGER/CASHIER/KITCHEN/ATTENDANT). See UserRole's javadoc
-- for why including this column now is reasonable schema preparation even
-- though RBAC enforcement itself (FARELO-122) doesn't exist yet.
--
-- email UNIQUE: it will be the login identifier once FARELO-121 exists,
-- even though the login mechanism itself doesn't exist yet.

CREATE TABLE app_user (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20) NOT NULL
        CHECK (role IN ('ADMIN', 'MANAGER', 'CASHIER', 'KITCHEN', 'ATTENDANT')),
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uk_app_user_email UNIQUE (email)
);
