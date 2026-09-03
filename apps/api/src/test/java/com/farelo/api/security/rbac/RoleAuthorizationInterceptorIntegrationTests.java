package com.farelo.api.security.rbac;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end proof (FARELO-122) that the RBAC mechanism actually works over
 * a real Spring MVC request, hitting {@link RbacDemoTestController}
 * (test-only, see its javadoc) through the full interceptor +
 * argument-resolver pipeline registered by {@link RbacWebMvcConfig}. The
 * interceptor's branch logic itself is covered in isolation by
 * {@link RoleAuthorizationInterceptorTests}; this class exists to prove
 * those units are actually wired together correctly by Spring (annotation
 * lookup via the real {@code HandlerMethod} Spring constructs, the real
 * exception → HTTP status mapping in {@code ApiExceptionHandler}, and the
 * real header parsing on a real {@code HttpServletRequest}).
 *
 * <p>See {@link RoleAuthorizationInterceptorRegressionIntegrationTests} for
 * the companion proof that this mechanism, once registered, leaves every
 * existing production endpoint completely unaffected.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoleAuthorizationInterceptorIntegrationTests extends AbstractIntegrationTest {

    private static final String PASSWORD = "senha-forte-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @BeforeEach
    void cleanUserTable() {
        userRepository.deleteAll();
    }

    @Test
    void returnsUnauthorizedWithNoAuthorizationHeaderAtAll() throws Exception {
        mockMvc.perform(get("/_rbac-test/admin-only"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsUnauthorizedWithAMalformedToken() throws Exception {
        mockMvc.perform(get("/_rbac-test/admin-only")
                        .header("Authorization", "Bearer this-is-not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void returnsForbiddenWhenTheTokensRoleIsNotAllowed() throws Exception {
        User user = userRepository.save(
                new User("Ana Souza", "ana@farelo.dev", passwordEncoder.encode(PASSWORD), UserRole.ATTENDANT));
        String token = jwtTokenService.issue(user).token();

        mockMvc.perform(get("/_rbac-test/admin-only")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsOkAndInjectsThePrincipalWhenTheTokensRoleIsAllowed() throws Exception {
        User user = userRepository.save(
                new User("Bia Rocha", "bia@farelo.dev", passwordEncoder.encode(PASSWORD), UserRole.ADMIN));
        String token = jwtTokenService.issue(user).token();

        mockMvc.perform(get("/_rbac-test/admin-only")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("hello bia@farelo.dev"));
    }

}
