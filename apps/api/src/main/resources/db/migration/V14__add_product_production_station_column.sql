-- V14__add_product_production_station_column.sql
-- Catalog domain (FARELO-073): which physical station (BAR/KITCHEN)
-- prepares a product — used to route printed tickets per station once
-- FARELO-074 splits PrintJobs by it (prompt mestre seção 12).
--
-- NULLable, unlike V4's available_on_menu/available_on_pos (which got
-- NOT NULL DEFAULT TRUE — an unambiguous safe default). There is no safe
-- default here: fabricating one (e.g. always 'KITCHEN') would silently
-- mis-tag existing products that don't actually belong there. Existing
-- rows are left NULL ("not yet assigned") for staff to set explicitly per
-- product; no backfill data migration.
--
-- VARCHAR + CHECK constraint, same convention as command.status
-- (V5__create_command_table.sql) and orders.status (V7), mirroring the
-- Java enum's allowed values at the DB level too.
ALTER TABLE product
    ADD COLUMN production_station VARCHAR(20)
        CHECK (production_station IN ('BAR', 'KITCHEN'));
