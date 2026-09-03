package com.farelo.api.security.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.AuthenticatedPrincipal;
import com.farelo.api.security.auth.JwtTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code POST /api/v1/auth/login} (FARELO-121),
 * against a real PostgreSQL instance (Testcontainers) — same shared
 * singleton-container reasoning as {@code UserControllerIntegrationTests},
 * hence the same {@code @BeforeEach} table cleanup.
 *
 * <p>Deliberately does <b>not</b> assert a real plaintext password or the
 * stored BCrypt hash anywhere beyond what {@code UserControllerIntegrationTests}
 * already establishes as acceptable practice (a fixed literal test password,
 * never logged) — see that class's javadoc.
 *
 * <p>The two "wrong credentials" tests
 * ({@link #returnsGenericErrorForWrongPassword()},
 * {@link #returnsGenericErrorForUnknownEmail()}) assert the exact same
 * status/code/message, on purpose — proving the two cases are genuinely
 * indistinguishable from the response, per
 * {@code InvalidCredentialsException}'s javadoc, rather than merely each
 * individually returning "some 401".
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTests extends AbstractIntegrationTest {

    private static final String PASSWORD = "senha-forte-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanUserTable() {
        userRepository.deleteAll();
    }

    @Test
    void issuesTokenForCorrectEmailAndPassword() throws Exception {
        userRepository.save(new User("Ana Souza", "ana@farelo.dev", passwordEncoder.encode(PASSWORD), UserRole.MANAGER));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "ana@farelo.dev", "password": "senha-forte-123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void issuedTokenParsesBackToTheLoggedInUser() throws Exception {
        User user = userRepository.save(
                new User("Bia Rocha", "bia@farelo.dev", passwordEncoder.encode(PASSWORD), UserRole.ATTENDANT));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "bia@farelo.dev", "password": "senha-forte-123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        AuthenticatedPrincipal principal = jwtTokenService.parse(body.get("token").asText());

        assertThat(principal.userId()).isEqualTo(user.getId());
        assertThat(principal.email()).isEqualTo("bia@farelo.dev");
        assertThat(principal.role()).isEqualTo(UserRole.ATTENDANT);
    }

    @Test
    void returnsGenericErrorForWrongPassword() throws Exception {
        userRepository.save(new User("Carlos", "carlos@farelo.dev", passwordEncoder.encode(PASSWORD), UserRole.CASHIER));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "carlos@farelo.dev", "password": "senha-errada-456"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid credentials"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsGenericErrorForUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "ninguem@farelo.dev", "password": "senha-forte-123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid credentials"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsGenericErrorForInactiveUserEvenWithCorrectPassword() throws Exception {
        User user = new User("Duda", "duda@farelo.dev", passwordEncoder.encode(PASSWORD), UserRole.KITCHEN);
        user.setActive(false);
        userRepository.save(user);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "duda@farelo.dev", "password": "senha-forte-123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void rejectsBlankEmailWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "", "password": "senha-forte-123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsBlankPasswordWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "alguem@farelo.dev", "password": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

}
