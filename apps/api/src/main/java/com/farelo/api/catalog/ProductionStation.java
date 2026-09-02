package com.farelo.api.catalog;

/**
 * The physical station that prepares a {@link Product} — used to route a
 * printed kitchen/bar ticket to the right place once an order is placed
 * (prompt mestre seção 12, Epic 6/"Impressão por setor": a pedido with
 * items from different stations should print one ticket per station, e.g.
 * 2 Cappuccino + 1 Coca-Cola go to the {@code BAR} ticket, 1 Croissant goes
 * to the {@code KITCHEN} ticket).
 *
 * <p><b>FARELO-073 scope</b>: this ticket only adds the field to
 * {@link Product}. Actually splitting {@code PrintJob}s per station when an
 * order is created is FARELO-074, a separate ticket.
 *
 * <p><b>Why only {@code BAR}/{@code KITCHEN}</b>: these are the two example
 * values given verbatim in the prompt mestre, and they already cover the
 * natural split for a cafeteria's production flow — drinks/espresso-based
 * items prepared at the counter ({@code BAR}) vs. food that needs actual
 * cooking/prep ({@code KITCHEN}). No third value (e.g. a dedicated
 * "dessert"/"cold prep" station) is added here without a concrete need
 * driving it — same YAGNI reasoning as AGENTS.md ("não criar abstrações
 * prematuras"). A future ticket can extend this enum if/when the physical
 * layout of the cafeteria actually grows a third station; extending it later
 * only costs a follow-up migration to widen the {@code CHECK} constraint
 * (same trade-off already accepted for {@code CommandStatus}/{@code
 * OrderStatus}, see V5/V7 migrations).
 */
public enum ProductionStation {
    BAR,
    KITCHEN
}
