package com.farelo.api.security.rbac;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The test that actually proves FARELO-122's scope boundary held: with
 * {@link RoleAuthorizationInterceptor} registered application-wide (via
 * {@link RbacWebMvcConfig}), a handful of pre-existing endpoints — picked
 * from three different, unrelated domains — must still return {@code 200}
 * with <b>no</b> {@code Authorization} header at all, exactly as they did
 * before this ticket. None of these controllers are annotated with
 * {@link RequireRole}; if any of them started requiring a token, that would
 * mean the interceptor stopped being purely annotation-driven (see
 * {@link RoleAuthorizationInterceptor}'s javadoc, step 2 of the pipeline) —
 * exactly the regression this ticket's instructions call out as the one
 * to guard against.
 *
 * <p>Endpoints chosen deliberately span domains untouched by this ticket
 * and not on each other's dependency path: {@code catalog}
 * ({@code GET /api/v1/categories}), {@code inventory}
 * ({@code GET /api/v1/ingredients}), and {@code notification}
 * ({@code GET /api/v1/notifications}) — none of them {@code inventory}/
 * {@code ordering} internals this ticket was told not to touch, but real,
 * already-shipped, unauthenticated-by-design production endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoleAuthorizationInterceptorRegressionIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void categoriesListingStillWorksWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk());
    }

    @Test
    void ingredientsListingStillWorksWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/ingredients"))
                .andExpect(status().isOk());
    }

    @Test
    void notificationsListingStillWorksWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk());
    }

}
