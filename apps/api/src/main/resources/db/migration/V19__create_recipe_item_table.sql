-- V19__create_recipe_item_table.sql
-- Inventory domain (FARELO-092): one line of a recipe's composition — an
-- ingredient and the quantity of it consumed per unit sold of the recipe's
-- product (see RecipeItem's javadoc). Follows recipe (V17) and ingredient
-- (V16).
--
-- recipe_id/ingredient_id: both @ManyToOne NOT NULL, mirroring RecipeItem's
-- Java mapping. No FK from recipe back to this table (no recipe_item
-- collection on the Recipe entity) — see RecipeItem's javadoc for why the
-- relationship is intentionally left unidirectional.
--
-- quantity: NUMERIC(12,3), not double/float (AGENTS.md) — see RecipeItem's
-- javadoc for the precision/scale reasoning (money-like exactness, but three
-- decimal places instead of two to comfortably represent small fractional
-- weights/volumes).
--
-- UNIQUE(recipe_id, ingredient_id): the actual source of truth for "an
-- ingredient appears at most once per recipe" — enforced at the DB level
-- (not just RecipeItemService#create's pre-check) because two concurrent
-- create requests could otherwise both pass an application-level check
-- before either commits, same reasoning as recipe's own partial unique
-- index in V17. Unlike that one, this is a plain column-level UNIQUE (no
-- WHERE clause needed): every RecipeItem row is "live" — there's no
-- active/inactive distinction on this table (see RecipeItem's javadoc on
-- why deletion is physical, not a soft flag).

CREATE TABLE recipe_item (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipe_id     UUID NOT NULL REFERENCES recipe (id),
    ingredient_id UUID NOT NULL REFERENCES ingredient (id),
    quantity      NUMERIC(12, 3) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT uq_recipe_item_recipe_id_ingredient_id UNIQUE (recipe_id, ingredient_id)
);

CREATE INDEX idx_recipe_item_recipe_id ON recipe_item (recipe_id);
