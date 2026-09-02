-- V13__create_printer_table.sql
-- Printing domain (FARELO-070): first table of the printing domain.
-- Printer represents a physical printing device (e.g. "Impressora Bar").
-- Foundation for PrintJob (FARELO-071) and per-productionStation routing
-- (FARELO-073/074) — see docs/domain-model.md.

CREATE TABLE printer (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(120) NOT NULL,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
