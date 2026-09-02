-- V16__create_ingredient_table.sql
-- Inventory domain (FARELO-090): first table of the inventory domain.
-- Ingredient represents an item used in a product's recipe (e.g. "Leite",
-- "Café em grão", "Copo 300ml"). No stock balance columns yet
-- (currentStock/minimumStock/criticalStock are FARELO-095/099) and no unit
-- cost (not requested by any ticket up to this one) — see docs/domain-model.md.
--
-- unit uses the same VARCHAR + CHECK constraint convention as
-- product.production_station (V14) / command.status (V5) / orders.status
-- (V7), mirroring the Java enum's allowed values at the DB level too.
-- Deliberately only GRAM/MILLILITER/UNIT (base units), not the full
-- UN/G/KG/ML/L list from the prompt mestre seção 14 — see IngredientUnit's
-- javadoc for why KG/L are purchase-unit concerns, not base stock units.

CREATE TABLE ingredient (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(120) NOT NULL,
    unit       VARCHAR(20) NOT NULL
        CHECK (unit IN ('GRAM', 'MILLILITER', 'UNIT')),
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
