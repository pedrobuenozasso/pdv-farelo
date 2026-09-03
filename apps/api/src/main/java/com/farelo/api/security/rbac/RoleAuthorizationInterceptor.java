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
 * The RBAC enforcement mechanism itself (FARELO-122): for any request whose
 * handler is annotated (method or class) with {@link RequireRole}, resolves
 * the caller's identity from the {@code Authorization} header and rejects
 * the request unless that identity's role is allowed. Every other request —
 * i.e. every endpoint in this codebase as of this ticket, since none use
 * {@link RequireRole} yet — passes through completely untouched. See
 * {@code docs/domain-model.md} ({@code security} section, FARELO-122
 * subsection) for the full design writeup this class implements.
 *
 * <h2>Mechanism — plain {@code HandlerInterceptor}, not
 * {@code spring-boot-starter-security}</h2>
 *
 * Same constraint FARELO-120/121 already operated under (see
 * {@code PasswordEncoderConfig} and {@code JwtTokenService}'s javadocs): the
 * full Spring Security starter autoconfigures a login-protected filter
 * chain the moment it's on the classpath, 401-ing every existing endpoint
 * unless a {@code SecurityFilterChain} bean says otherwise — which would
 * mean writing a {@code permitAll()}-shaped policy inside a ticket whose
 * instructions explicitly forbid deciding endpoint policy (that's
 * FARELO-123/124). A plain Spring MVC {@link HandlerInterceptor}, registered
 * via {@code RbacWebMvcConfig} ({@link org.springframework.web.servlet.config.annotation.WebMvcConfigurer}),
 * has no such autoconfiguration side effect: it only ever runs the checks
 * below, and only for handlers actually carrying {@link RequireRole}. No new
 * dependency was needed — {@link JwtTokenService#parse} (FARELO-121) already
 * does 100% of the token verification work; this class is just the HTTP
 * wiring around it (header extraction, annotation lookup, role comparison,
 * turning failures into the two HTTP outcomes the ticket asks for).
 *
 * <h2>Request pipeline</h2>
 *
 * <ol>
 *   <li>Not a {@link HandlerMethod} (e.g. a static resource handler) →
 *   pass through.</li>
 *   <li>No {@link RequireRole} on the method, and none on its declaring
 *   class → pass through. Method-level takes priority over class-level when
 *   both are present (see {@link RequireRole}'s javadoc); the two are never
 *   merged.</li>
 *   <li>{@code Authorization} header missing or not shaped
 *   {@code "Bearer <token>"} → {@link InvalidTokenException} → mapped by
 *   {@code ApiExceptionHandler} to {@code 401 Unauthorized}.</li>
 *   <li>Header present but {@link JwtTokenService#parse} rejects the token
 *   (bad signature, malformed, expired) → same {@link InvalidTokenException}
 *   → same {@code 401}. Deliberately the same outcome as "no header at
 *   all": from the caller's perspective both mean "you are not
 *   authenticated", and collapsing them avoids telling an attacker whether
 *   a presented token was merely missing vs. actively rejected.</li>
 *   <li>Token valid, but {@link AuthenticatedPrincipal#role()} is not one of
 *   {@link RequireRole#value()} → {@link InsufficientRoleException} →
 *   mapped to {@code 403 Forbidden}. The caller's identity IS established
 *   here (unlike the 401 case above) — they're just not allowed to do
 *   this.</li>
 *   <li>Otherwise: the principal is stashed on the request (see
 *   {@link #PRINCIPAL_REQUEST_ATTRIBUTE}) so a controller method can declare
 *   an {@link AuthenticatedPrincipal} parameter and have it injected by
 *   {@link AuthenticatedPrincipalArgumentResolver} — the "make the resulting
 *   principal available to controllers" half of this ticket's brief — and
 *   the request proceeds normally.</li>
 * </ol>
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
            return true;
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
