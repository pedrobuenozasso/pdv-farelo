-- V4__add_product_availability_columns.sql
-- Catalog domain (FARELO-017): products control visibility on the QR menu
-- and the POS independently (see prompt mestre, seção 4).
-- NOT NULL DEFAULT TRUE so existing rows stay available on both channels.

ALTER TABLE product
    ADD COLUMN available_on_menu BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN available_on_pos BOOLEAN NOT NULL DEFAULT TRUE;
