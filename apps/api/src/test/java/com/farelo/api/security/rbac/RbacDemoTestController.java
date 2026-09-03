package com.farelo.api.security.rbac;

import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only controller (lives in {@code src/test/java}, never packaged into
 * the running application) that exists solely to prove the FARELO-122 RBAC
 * mechanism ({@link RequireRole} + {@link RoleAuthorizationInterceptor} +
 * {@link AuthenticatedPrincipalArgumentResolver}) works end-to-end over a
 * real MockMvc round trip — see
 * {@link RoleAuthorizationInterceptorIntegrationTests}.
 *
 * <p>Deliberately <b>not</b> a real production controller and not under
 * {@code /api/v1/...} the way any real endpoint would be — this ticket's
 * scope explicitly forbids annotating any existing endpoint with
 * {@code @RequireRole} (see {@code docs/domain-model.md}, {@code security}
 * section, FARELO-122 subsection). Because it lives in the test source
 * tree under the same base package Spring Boot component-scans
 * ({@code com.farelo.api}, from {@code FareloApiApplication}), it's picked
 * up automatically by any {@code @SpringBootTest} in this module and is
 * never present on the production classpath.
 */
@RestController
@RequestMapping("/_rbac-test")
public class RbacDemoTestController {

    @GetMapping("/admin-only")
    @RequireRole(UserRole.ADMIN)
    public String adminOnly(AuthenticatedPrincipal principal) {
        return "hello " + principal.email();
    }

}
