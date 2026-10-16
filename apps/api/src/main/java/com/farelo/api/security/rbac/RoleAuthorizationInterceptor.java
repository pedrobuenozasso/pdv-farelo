package com.farelo.api.security.rbac;

import com.farelo.api.security.auth.AuthenticatedPrincipal;
import com.farelo.api.security.auth.InvalidTokenException;
import com.farelo.api.security.auth.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * Enforces the explicit access policy of each Spring MVC API handler.
 * A handler must have {@link RequireRole} or {@link PublicEndpoint};
 * an unclassified handler fails closed. Role-protected handlers validate
 * the Bearer token and expose the verified principal to argument resolvers.
 * Non-{@link HandlerMethod} infrastructure handlers pass through.
 */
@Component
public class RoleAuthorizationInterceptor implements HandlerInterceptor {

    /**
     * Request attribute key {@link AuthenticatedPrincipal} is stashed under
     * once a {@link RequireRole}-protected request passes its checks.
     * Package-private on purpose — {@link AuthenticatedPrincipalArgumentResolver}
     * is the only intended reader; controllers get the principal via a
     * method parameter, not by reading request attributes directly.
     */
    static final String PRINCIPAL_REQUEST_ATTRIBUTE = RoleAuthorizationInterceptor.class.getName() + ".PRINCIPAL";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;

    public RoleAuthorizationInterceptor(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireRole requireRole = resolveRequireRole(handlerMethod);
        if (requireRole == null) {
            if (handlerMethod.hasMethodAnnotation(PublicEndpoint.class)
                    || handlerMethod.getBeanType().isAnnotationPresent(PublicEndpoint.class)) {
                return true;
            }
            throw new IllegalStateException("API handler missing @RequireRole or @PublicEndpoint: "
                    + handlerMethod.getBeanType().getSimpleName() + "." + handlerMethod.getMethod().getName());
        }

        AuthenticatedPrincipal principal = resolvePrincipal(request);
        if (!Arrays.asList(requireRole.value()).contains(principal.role())) {
            throw new InsufficientRoleException(principal.role());
        }

        request.setAttribute(PRINCIPAL_REQUEST_ATTRIBUTE, principal);
        return true;
    }

    // Method-level annotation wins over class-level — see RequireRole's javadoc.
    private RequireRole resolveRequireRole(HandlerMethod handlerMethod) {
        RequireRole methodLevel = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (methodLevel != null) {
            return methodLevel;
        }
        return handlerMethod.getBeanType().getAnnotation(RequireRole.class);
    }

    private AuthenticatedPrincipal resolvePrincipal(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new InvalidTokenException("Missing or malformed Authorization header", null);
        }

        String token = header.substring(BEARER_PREFIX.length());
        return jwtTokenService.parse(token);
    }

}
