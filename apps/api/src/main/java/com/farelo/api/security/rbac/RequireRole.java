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
 * <p><b>FARELO-122 scope — nothing uses this annotation yet</b>: this
 * ticket builds the mechanism only (see {@code docs/domain-model.md},
 * {@code security} section, FARELO-122 subsection, for the full design
 * writeup and for why). No production controller
 * ({@code CategoryController}, {@code ProductController},
 * {@code CommandController}, {@code OrderController},
 * {@code PrintJobController}, {@code IngredientController},
 * {@code RecipeController}, {@code UserController}, {@code AuthController},
 * {@code NotificationController} — every controller that exists as of this
 * ticket) is annotated with {@code @RequireRole}. Deciding *which* roles
 * may call *which* real endpoint is deliberately deferred to FARELO-123
 * (Admin surface) and FARELO-124 (PDV/kitchen surface) — this ticket only
 * proves the mechanism works, against a dedicated test-only controller (see
 * {@code RoleAuthorizationInterceptorIntegrationTests}).
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    UserRole[] value();

}
