-- V44__add_command_table_id_column.sql
-- FARELO-315: an OPTIONAL link from a comanda to the physical dining_table
-- it's currently associated with. Nullable, no default — NULL means "no
-- table" (delivery/balcão, cardápio QR without table service), a
-- legitimate and common permanent state, never a placeholder. This is
-- deliberately the ONLY direction of this relationship: dining_table gets
-- no column pointing back to command (see DiningTable/Command's javadocs
-- for the full reasoning) — a comanda-without-table flow that already
-- works today must keep working exactly as it does, unchanged.
--
-- No FK ON DELETE behavior specified (defaults to NO ACTION/RESTRICT):
-- dining_table rows are never deleted by anything in this codebase yet
-- (same "no delete path" state most entities start in), so this doesn't
-- need to be decided now.

ALTER TABLE command
    ADD COLUMN table_id UUID REFERENCES dining_table (id);
