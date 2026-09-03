package com.farelo.api.security.rbac;

import com.farelo.api.security.UserRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a controller method (or every method of an annotated
 * controller class) may only be called by a caller whose
 * {@link com.farelo.api.security.auth.AuthenticatedPrincipal#role()} is one
 * of {@link #value()}. Enforced by {@link RoleAuthorizationInterceptor} —
 * this annotation by itself does nothing; see that class's javadoc for the
 * full request pipeline (FARELO-122).
 *
 * <p><b>Method-level wins over class-level</b>: if a class is annotated
 * (e.g. "every endpoint in this controller needs {@code ADMIN}") and one of
 * its methods carries its own {@code @RequireRole} (e.g. "except this one,
 * which also allows {@code MANAGER}"), the method's list is what's checked
 * — the two are never merged/unioned. This mirrors how Spring's own
 * meta-annotations resolve method vs. type level when both are present, and
 * keeps the check trivial to reason about at each handler: "read the one
 * annotation closest to this method".
 *
 * <p><b>FARELO-122 scope — mechanism only, no production controller
 * annotated yet</b>: FARELO-122 itself built only this mechanism (see
 * {@code docs/domain-model.md}, {@code security} section, FARELO-122
 * subsection) — as of that ticket, no production controller used
 * {@code @RequireRole} at all; the only proof it worked was a dedicated
 * test-only controller (see {@code RoleAuthorizationInterceptorIntegrationTests}).
 * <b>FARELO-123 is the first ticket to actually apply it</b>: the Admin
 * surface — {@code CategoryController}/{@code ProductController} (write
 * methods only) and {@code UserController} (see that subsection of
 * {@code docs/domain-model.md} for exactly which methods and roles, and the
 * reasoning).
 *
 * <p><b>FARELO-124 applies it to the PDV/kitchen surface</b>: most of
 * {@code CommandController} (except {@code findByNumber}, a public
 * "Cardápio QR" dependency), {@code OrderController} (except
 * {@code create}, also a public dependency), {@code CommandOrdersController},
 * and only {@code PrintJobController#retry} — the other three
 * {@code PrintJobController} endpoints are called exclusively by the Farelo
 * Edge Agent, a machine with no user login, so {@code @RequireRole} doesn't
 * apply to them (see that class's javadoc for the full reasoning). See the
 * FARELO-124 subsection of {@code docs/domain-model.md} for exactly which
 * methods and roles. Still deliberately untouched: {@code
 * IngredientController}, {@code RecipeController}, {@code AuthController},
 * {@code NotificationController} — out of scope for both FARELO-123 and
 * FARELO-124 (inventory/notification RBAC, if ever needed, is a distinct
 * future ticket).
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    UserRole[] value();

}
