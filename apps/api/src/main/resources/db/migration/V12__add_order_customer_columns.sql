-- V12__add_order_customer_columns.sql
-- Ordering domain: persist the customer name/phone already collected by the
-- QR checkout form (apps/web, FARELO-045) but never sent to the backend
-- until now (docs/api.md's POST /api/v1/orders previously noted these
-- fields stayed frontend-only).
--
-- Deliberately a plain snapshot on the order itself, NOT a new `customer`
-- domain/entity (see docs/domain-model.md's `customer` row and the
-- `ordering` section) — same spirit as OrderItem's unitPrice price
-- snapshot: captured at order-creation time, never a reference to some
-- other row.
--
-- Both nullable: not every order-creation flow necessarily carries this
-- data (e.g. a future POS-direct order flow that skips the QR checkout
-- form entirely).

ALTER TABLE orders
    ADD COLUMN customer_name  VARCHAR(120),
    ADD COLUMN customer_phone VARCHAR(30);
