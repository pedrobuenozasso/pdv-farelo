-- V2__create_category_table.sql
-- Catalog domain (FARELO-010): first business table.
-- Category is part of the single source of truth for the menu
-- (see docs/domain-model.md).

CREATE TABLE category (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(120) NOT NULL,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
