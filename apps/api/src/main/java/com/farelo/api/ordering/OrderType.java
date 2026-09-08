package com.farelo.api.ordering;

/**
 * How an {@link Order} entered the system (FARELO-312): {@code COMANDA} —
 * every order until this ticket, tied to a {@link
 * com.farelo.api.command.Command} the customer/staff is running a tab
 * against (prompt mestre seção 6-7); {@code BALCAO} — a walk-up counter
 * sale with no comanda at all ("cliente recebe/leva na hora", the Sischef
 * gap this ticket closes — see docs/gaps-sischef-pdv.md, item 2).
 *
 * <p>{@code @Enumerated(EnumType.STRING)} on {@link Order#getType()}, never
 * {@code ORDINAL} — same reasoning as every other status/type enum in this
 * codebase (e.g. {@link OrderStatus}): storing the ordinal would silently
 * corrupt data if this enum's declaration order ever changes.
 *
 * <h2>Why this couples to {@code command} being nullable</h2>
 *
 * A {@code BALCAO} order has no comanda to attach to by definition, so
 * {@link Order#getCommand()} had to become optional (see that class's
 * javadoc, "Design decision — BALCAO"). {@code
 * ck_orders_type_command_scope} (V39__add_order_type_and_nullable_command
 * .sql) enforces the exclusivity — {@code COMANDA} always has a {@code
 * command_id}, {@code BALCAO} never does — at the database level too, the
 * same defense-in-depth convention {@code ck_print_job_type_scope} (V36)
 * and {@code ck_discount_percentage_scope} (V37) already established for
 * an analogous "type says which optional column is populated" shape.
 */
public enum OrderType {
    COMANDA,
    BALCAO
}
