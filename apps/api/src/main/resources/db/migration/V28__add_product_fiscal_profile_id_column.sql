-- V28__add_product_fiscal_profile_id_column.sql
-- Catalog domain (FARELO-151): associates a Product with the FiscalProfile
-- (V27) it belongs to, so products sharing the same tax treatment don't
-- need to repeat those attributes individually (prompt mestre seção 22/23).
--
-- NULLable, same reasoning as V14's production_station: there is no safe
-- default to fabricate (unlike V4's available_on_menu/available_on_pos,
-- which got NOT NULL DEFAULT TRUE). Existing rows are left NULL ("not yet
-- classified") for staff to assign explicitly per product; no backfill.
--
-- FK + index, same convention as category_id (V3)/every other FK column in
-- this codebase (e.g. idx_payment_command_id, V26) — including nullable
-- ones (e.g. inventory_movement.order_id, V21).
ALTER TABLE product
    ADD COLUMN fiscal_profile_id UUID REFERENCES fiscal_profile (id);

CREATE INDEX idx_product_fiscal_profile_id ON product (fiscal_profile_id);
