package com.farelo.api.security;

/**
 * The operational profile of a {@link User} within Farelo OS.
 *
 * <p><b>FARELO-120 scope — why a {@code role} column exists already, even
 * though RBAC enforcement (FARELO-122) does not</b>: nothing reads this
 * field to grant/deny anything yet — there is no Spring Security, no
 * protected endpoint, no permission check anywhere in the codebase as of
 * this ticket (see docs/PROMPT_MESTRE.md seção 26/EPIC 9: FARELO-121
 * authentication, FARELO-122 RBAC, FARELO-123/124 protecting Admin/PDV are
 * all separate, future tickets). Two things tipped the decision towards
 * including it now rather than waiting for FARELO-122:
 *
 * <ol>
 *   <li>The prompt mestre (seção 26) already names a closed, literal list of
 *   five profiles — {@code ADMIN}, {@code MANAGER}, {@code CASHIER},
 *   {@code KITCHEN}, {@code ATTENDANT} — for this exact purpose. Modeling
 *   them isn't guessing a design FARELO-122 hasn't decided yet; it's
 *   transcribing a decision the spec already made, the same way {@code
 *   ProductionStation} transcribes the {@code BAR}/{@code KITCHEN} example
 *   literally given for FARELO-073.</li>
 *   <li>A user's role is intrinsic to who the person is at the café (a
 *   cashier vs. a manager), not something layered on afterwards — modeling
 *   {@code User} without it would mean every one of the five profiles looks
 *   identical until FARELO-122 lands, which then has no choice but to widen
 *   this exact table with a backfill for existing rows. Adding the column
 *   now, unread by anything, costs nothing operationally (default-free,
 *   required at creation, plain enum column) and avoids that follow-up
 *   migration.</li>
 * </ol>
 *
 * <p>What's deliberately <b>not</b> done here: no permission/authority
 * mapping, no many-to-many role↔permission table (seção 21 lists
 * "Usuários" and "Permissões" as separate Admin modules — a dedicated
 * permission model, if the product ever needs finer granularity than five
 * fixed roles, is FARELO-122's call to make, not guessed at here), and no
 * enforcement of any kind. This is exactly and only a labelled column.
 *
 * <p>{@code VARCHAR} + {@code CHECK} at the DB level (see
 * {@code V19__create_user_table.sql}), same convention as {@code
 * IngredientUnit}/{@code ProductionStation}/{@code CommandStatus} —
 * extending this list later costs a follow-up migration to widen the
 * constraint, a trade-off already accepted project-wide for closed-set
 * enums like this one.
 */
public enum UserRole {
    ADMIN,
    MANAGER,
    CASHIER,
    KITCHEN,
    ATTENDANT
}
