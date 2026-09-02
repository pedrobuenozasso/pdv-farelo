-- V6__seed_commands_1_to_100.sql
-- Command domain (FARELO-031): seeds the 100 physical comandas used by the
-- venue. status is omitted — the column's own default (AVAILABLE) applies.

INSERT INTO command (number)
SELECT generate_series(1, 100);
