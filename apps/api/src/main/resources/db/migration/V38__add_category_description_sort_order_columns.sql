-- V38__add_category_description_sort_order_columns.sql
-- FARELO-261 ("Criar categoria pelo Admin"): the ticket's own field list
-- (name/description/active/sortOrder) named two columns that never
-- existed — category only had name/active until now.
--
-- description is nullable (optional, same convention as product.description).
-- sort_order defaults to 0 for every existing row and every future insert
-- that doesn't specify one; CategoryService#listAll now orders by it (then
-- name) — actually reordering categories via the Admin UI is FARELO-264, a
-- separate, later ticket; this migration only adds the column and a stable
-- default sort, so every category starts equal-priority (alphabetical)
-- until FARELO-264 gives staff a way to change it.

ALTER TABLE category
    ADD COLUMN description TEXT,
    ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;
