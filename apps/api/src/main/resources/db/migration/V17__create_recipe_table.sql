-- V17__create_recipe_table.sql
-- Inventory domain (FARELO-091): the "header" of a product's recipe /
-- ficha técnica (see Recipe's javadoc). No recipe items yet — the list of
-- ingredients + quantities is RecipeItem (FARELO-092), a follow-up
-- migration/table.
--
-- product_id: @ManyToOne, not @OneToOne (see Recipe's javadoc for the full
-- reasoning) — a product can accumulate multiple Recipe rows over time
-- (deactivated history + one current active one), rather than a single row
-- that gets mutated/replaced in place.
--
-- The partial unique index below is the actual source of truth for "at
-- most one active recipe per product" — enforced at the DB level (not just
-- RecipeService#create's pre-check) because two concurrent create requests
-- could otherwise both pass an application-level check before either
-- commits. Postgres supports a WHERE clause on CREATE UNIQUE INDEX, unlike
-- a plain column-level UNIQUE constraint (which can't express "unique only
-- among active rows").

CREATE TABLE recipe (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES product (id),
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_recipe_product_id_active ON recipe (product_id) WHERE active;
