package com.farelo.api.security.rbac;

import com.farelo.api.security.auth.AuthenticatedPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Lets a {@link RequireRole}-protected controller method simply declare an
 * {@link AuthenticatedPrincipal} parameter and receive the caller resolved
 * by {@link RoleAuthorizationInterceptor} — the "make the resulting
 * principal available to controllers" half of FARELO-122's brief, the same
 * shape as Spring Security's own {@code @AuthenticationPrincipal}, minus the
 * annotation (a plain type match is enough here: nothing else in this
 * codebase would ever want an {@code AuthenticatedPrincipal} parameter for
 * any other reason).
 *
 * <p>Only meaningful on a method the interceptor actually protected: it
 * reads the request attribute {@link RoleAuthorizationInterceptor}
 * populates only after a {@link RequireRole} check passes. A parameter of
 * this type on an <em>unprotected</em> handler (no {@code @RequireRole})
 * would resolve to {@code null} — harmless (Java allows a {@code null}
 * argument here), but not a supported/tested combination, since nothing in
 * this ticket's scope wires {@code @RequireRole} onto a real controller.
 */
@Component
public class AuthenticatedPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AuthenticatedPrincipal.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
        return request.getAttribute(RoleAuthorizationInterceptor.PRINCIPAL_REQUEST_ATTRIBUTE);
    }

}
