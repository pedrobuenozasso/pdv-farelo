package com.farelo.api.security.rbac;

import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.AuthenticatedPrincipal;
import com.farelo.api.security.auth.InvalidTokenException;
import com.farelo.api.security.auth.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Focused unit test of {@link RoleAuthorizationInterceptor#preHandle}
 * (FARELO-122), mocking {@link JwtTokenService}/{@link HttpServletRequest}
 * directly rather than going through the full Spring MVC stack — that
 * round trip is covered separately by
 * {@link RoleAuthorizationInterceptorIntegrationTests}. {@link HandlerMethod}
 * instances here wrap real methods on small local fixture classes (not
 * mocks) because {@code HandlerMethod#getMethodAnnotation}/{@code
 * getBeanType} do real reflection over the wrapped {@link java.lang.reflect.Method} —
 * mocking that class would just re-implement the annotation lookup the test
 * is trying to verify.
 */
class RoleAuthorizationInterceptorTests {

    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final RoleAuthorizationInterceptor interceptor = new RoleAuthorizationInterceptor(jwtTokenService);

    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);

    @Test
    void passesThroughWhenHandlerIsNotAHandlerMethod() {
        Object handler = new Object();

        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isTrue();
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void passesThroughWhenNeitherMethodNorClassIsAnnotated() throws Exception {
        HandlerMethod handlerMethod = handlerMethodFor(Unprotected.class, "handle");

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isTrue();
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void rejectsWithInvalidTokenWhenAuthorizationHeaderIsMissing() throws Exception {
        HandlerMethod handlerMethod = handlerMethodFor(ClassProtected.class, "handle");
        when(request.getHeader("Authorization")).thenReturn(null);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(InvalidTokenException.class);
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void rejectsWithInvalidTokenWhenHeaderIsNotBearerShaped() throws Exception {
        HandlerMethod handlerMethod = handlerMethodFor(ClassProtected.class, "handle");
        when(request.getHeader("Authorization")).thenReturn("Basic abc123");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(InvalidTokenException.class);
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void rejectsWithInvalidTokenWhenJwtTokenServiceRejectsTheToken() throws Exception {
        HandlerMethod handlerMethod = handlerMethodFor(ClassProtected.class, "handle");
        when(request.getHeader("Authorization")).thenReturn("Bearer bad-token");
        when(jwtTokenService.parse("bad-token")).thenThrow(new InvalidTokenException("bad token", null));

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsWithInsufficientRoleWhenCallerRoleIsNotAllowed() throws Exception {
        HandlerMethod handlerMethod = handlerMethodFor(ClassProtected.class, "handle");
        AuthenticatedPrincipal principal =
                new AuthenticatedPrincipal(UUID.randomUUID(), "someone@farelo.dev", UserRole.ATTENDANT);
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(jwtTokenService.parse("good-token")).thenReturn(principal);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(InsufficientRoleException.class);
    }

    @Test
    void allowsAndStashesPrincipalWhenCallerRoleIsAllowed() throws Exception {
        HandlerMethod handlerMethod = handlerMethodFor(MethodProtected.class, "handle");
        AuthenticatedPrincipal principal =
                new AuthenticatedPrincipal(UUID.randomUUID(), "someone@farelo.dev", UserRole.MANAGER);
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(jwtTokenService.parse("good-token")).thenReturn(principal);

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isTrue();
        verify(request).setAttribute(RoleAuthorizationInterceptor.PRINCIPAL_REQUEST_ATTRIBUTE, principal);
    }

    @Test
    void methodLevelAnnotationOverridesClassLevelRatherThanBeingMerged() throws Exception {
        // Class requires ADMIN, method requires KITCHEN. A KITCHEN caller
        // must be let through, proving the method-level annotation replaces
        // the class-level one outright instead of the two being unioned.
        HandlerMethod handlerMethod = handlerMethodFor(MethodOverridesClass.class, "handle");
        AuthenticatedPrincipal principal =
                new AuthenticatedPrincipal(UUID.randomUUID(), "someone@farelo.dev", UserRole.KITCHEN);
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(jwtTokenService.parse("good-token")).thenReturn(principal);

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isTrue();
    }

    private static HandlerMethod handlerMethodFor(Class<?> type, String methodName) throws Exception {
        return new HandlerMethod(type.getDeclaredConstructor().newInstance(), type.getMethod(methodName));
    }

    static class Unprotected {
        public void handle() {
        }
    }

    @RequireRole(UserRole.ADMIN)
    static class ClassProtected {
        public void handle() {
        }
    }

    static class MethodProtected {
        @RequireRole({UserRole.ADMIN, UserRole.MANAGER})
        public void handle() {
        }
    }

    @RequireRole(UserRole.ADMIN)
    static class MethodOverridesClass {
        @RequireRole(UserRole.KITCHEN)
        public void handle() {
        }
    }

}
