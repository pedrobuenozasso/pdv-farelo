package com.farelo.api.security.rbac;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Registers the FARELO-122 RBAC mechanism into the Spring MVC request
 * pipeline: {@link RoleAuthorizationInterceptor} (the enforcement itself)
 * and {@link AuthenticatedPrincipalArgumentResolver} (how a protected
 * controller method reads back who's calling).
 *
 * <p>No path pattern is configured on {@link InterceptorRegistry#addInterceptor} —
 * the interceptor is registered against every path (the {@code
 * WebMvcConfigurer} default) and relies entirely on
 * {@link RequireRole}'s presence/absence per handler to decide whether to
 * act, rather than on a URL allowlist/denylist. This is deliberate: an
 * annotation-driven check can't accidentally miss protecting a new
 * endpoint added under an already-restricted path prefix, and — the
 * concern that matters most for this ticket's scope boundary — it can't
 * accidentally start rejecting an existing endpoint that was never meant to
 * be touched. See {@code RoleAuthorizationInterceptorRegressionIntegrationTests}
 * for the regression proof that unannotated endpoints stay completely
 * unaffected.
 */
@Configuration
public class RbacWebMvcConfig implements WebMvcConfigurer {

    private final RoleAuthorizationInterceptor roleAuthorizationInterceptor;
    private final AuthenticatedPrincipalArgumentResolver authenticatedPrincipalArgumentResolver;

    public RbacWebMvcConfig(
            RoleAuthorizationInterceptor roleAuthorizationInterceptor,
            AuthenticatedPrincipalArgumentResolver authenticatedPrincipalArgumentResolver) {
        this.roleAuthorizationInterceptor = roleAuthorizationInterceptor;
        this.authenticatedPrincipalArgumentResolver = authenticatedPrincipalArgumentResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleAuthorizationInterceptor);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authenticatedPrincipalArgumentResolver);
    }

}
