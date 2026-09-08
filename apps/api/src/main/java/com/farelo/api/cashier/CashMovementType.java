package com.farelo.api.cashier;

/**
 * The four kinds of ad-hoc {@link CashMovement} this codebase supports —
 * the closed, literal set {@code docs/gaps-sischef-pdv.md} already gives
 * before either FARELO-310 or FARELO-311 started: {@code WITHDRAWAL}
 * (sangria — cash removed from the drawer, e.g. a bank deposit),
 * {@code SUPPLY} (suprimento — cash added to reinforce the float),
 * {@code EXPENSE} (despesa — an ad-hoc expense paid directly from the
 * drawer) and {@code INCOME} (receita — an ad-hoc income received directly
 * into the drawer, outside the order/payment flow).
 *
 * <p><b>Contrast with {@code AuditLog.action}</b>: that field stays an open
 * {@code String} specifically because its vocabulary genuinely isn't known
 * in advance (each future producer invents its own action name). Here the
 * opposite is true — the vocabulary is closed and fully given up front, the
 * same situation that made {@code PaymentMethod} a closed enum rather than
 * a {@code String} — so this is a {@code enum} + DB {@code CHECK}
 * (mirroring {@code PaymentMethod}'s own javadoc for the side-by-side
 * reasoning).
 *
 * <p>All four values are declared together in FARELO-310's own migration
 * ({@code V41__create_cash_movement_table.sql}) even though this ticket's
 * producers ({@code CashMovementService#recordWithdrawal}/{@code
 * #recordSupply}) only use {@code WITHDRAWAL}/{@code SUPPLY} — FARELO-311
 * reuses {@code EXPENSE}/{@code INCOME} without a follow-up migration to
 * widen the {@code CHECK} constraint.
 */
public enum CashMovementType {
    WITHDRAWAL,
    SUPPLY,
    EXPENSE,
    INCOME
}
