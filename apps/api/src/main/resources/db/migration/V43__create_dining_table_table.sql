-- V43__create_dining_table_table.sql
-- FARELO-315: starts the `table` domain — a physical table in the café's
-- dining room ("Mesa" in the Sischef comparison, docs/gaps-sischef-pdv.md
-- item 3), deliberately separate from `command`.
--
-- Table named `dining_table`, not `table` — TABLE is a reserved SQL
-- keyword; naming it literally `table` would break raw SQL that
-- references it unquoted, including future migrations (same reasoning as
-- V7's `orders`, not `order`). See DiningTable's javadoc for the full
-- naming rationale (including why the Java class is `DiningTable`, not
-- `Table`).
--
-- number: the human-facing business identifier (see DiningTable's
-- javadoc), UNIQUE + NOT NULL same convention as command.number (V5).
-- Unlike `command` (seeded 1-100 by a migration), tables are created
-- through the API, so DiningTableService#create also checks uniqueness
-- itself for a friendly 409 rather than relying only on this constraint.
--
-- capacity: no CHECK for positivity — same choice already made for
-- order_item.quantity (V8, validated only at the DTO layer via
-- @Positive), not a closed enum like command.status, so it doesn't get
-- the CHECK-constraint treatment those get.

CREATE TABLE dining_table (
    id          UUID PRIMARY KEY,
    number      INT NOT NULL UNIQUE,
    capacity    INT NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
