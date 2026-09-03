package com.farelo.api.security.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code POST}/{@code GET}/{@code PUT}/
 * {@code PATCH .../password} of {@code /api/v1/users}, against a real
 * PostgreSQL instance (Testcontainers).
 *
 * <p>Same reasoning as {@code IngredientControllerIntegrationTests}: the
 * shared singleton Postgres container (see {@link AbstractIntegrationTest})
 * means the {@code app_user} table may already have rows from other test
 * classes, so tests that assert list contents clear it first.
 *
 * <p>Two assertions here exist specifically because this is the {@code
 * security} domain (see FARELO-120's own instructions): {@link
 * #neverExposesPasswordHashInAnyResponse()} confirms {@code passwordHash} is
 * absent from the JSON of every endpoint, and
 * {@link #createsUserAndPersistsIt()}/{@link #hashesNewPasswordOnPasswordChange()}
 * confirm the stored value is a real hash, never equal to the plaintext
 * password sent in the request.
 *
 * <p><b>FARELO-123</b>: every endpoint in {@code UserController} now
 * requires {@link UserRole#ADMIN} (list/getById also allow
 * {@link UserRole#MANAGER}) — see that controller's javadoc. Every request
 * below mints a real token via {@link #tokenFor} (same pattern as
 * {@code RoleAuthorizationInterceptorIntegrationTests}, FARELO-122) and
 * sends it as {@code Authorization: Bearer <token>}; the caller ({@code
 * "Caller Admin"} et al.) is a throwaway account distinct from whatever
 * user each test is exercising, minted fresh so tests never collide on
 * unique email.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIntegrationTests extends AbstractIntegrationTest {

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

    // Mints a real token for a throwaway caller of the given role, then
    // deletes that caller's row again before returning — unlike
    // CategoryControllerIntegrationTests/ProductControllerIntegrationTests
    // (whose count assertions are against category/product tables, never
    // app_user), this class's own list()/getById() tests assert exact
    // counts/contents of app_user itself, so a caller left behind here
    // would silently inflate every list assertion below (e.g.
    // returnsEmptyListWhenNoUsersExist's hasSize(0)). Deleting the row is
    // safe only because a JWT is fully self-contained: JwtTokenService#parse
    // never touches the database (no revocation mechanism — see that
    // class's javadoc), so a deleted caller's already-issued token still
    // verifies exactly as if the row still existed.
    private String tokenFor(UserRole role) {
        User caller = userRepository.save(new User(
                "Caller " + role,
                "caller-%s@farelo.dev".formatted(UUID.randomUUID()),
                passwordEncoder.encode(PASSWORD),
                role));
        String token = jwtTokenService.issue(caller).token();
        userRepository.delete(caller);
        return token;
    }

    private String adminToken() {
        return tokenFor(UserRole.ADMIN);
    }

    @Test
    void createsUserAndPersistsIt() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Ana Souza", "email": "ana@farelo.dev", "password": "senha-forte-123", "role": "MANAGER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Ana Souza"))
                .andExpect(jsonPath("$.email").value("ana@farelo.dev"))
                .andExpect(jsonPath("$.role").value("MANAGER"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn();

        UserResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), UserResponse.class);

        Optional<User> persisted = userRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getEmail()).isEqualTo("ana@farelo.dev");
        // Proof the password is actually hashed: the stored value is never
        // the plaintext sent in the request, and it verifies as a match
        // through the real encoder (BCrypt), not just "some other string".
        assertThat(persisted.get().getPasswordHash()).isNotEqualTo("senha-forte-123");
        assertThat(passwordEncoder.matches("senha-forte-123", persisted.get().getPasswordHash())).isTrue();
    }

    @Test
    void neverExposesPasswordHashInAnyResponse() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bia Rocha", "email": "bia@farelo.dev", "password": "senha-forte-123", "role": "ATTENDANT"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID id = UUID.fromString(created.get("id").asText());

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());

        mockMvc.perform(get("/api/v1/users/{id}", id)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(put("/api/v1/users/{id}", id)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bia Rocha", "email": "bia@farelo.dev", "role": "ATTENDANT", "active": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(patch("/api/v1/users/{id}/password", id)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword": "outra-senha-456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void returnsConflictWhenEmailAlreadyExists() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Primeiro", "email": "duplicado@farelo.dev", "password": "senha-forte-123", "role": "CASHIER"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Segundo", "email": "duplicado@farelo.dev", "password": "outra-senha-456", "role": "CASHIER"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsBlankNameWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "email": "valido@farelo.dev", "password": "senha-forte-123", "role": "CASHIER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsInvalidEmailWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Nome", "email": "nao-e-um-email", "password": "senha-forte-123", "role": "CASHIER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsShortPasswordWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Nome", "email": "valido@farelo.dev", "password": "123", "role": "CASHIER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsMissingRoleWithStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Nome", "email": "valido@farelo.dev", "password": "senha-forte-123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void returnsEmptyListWhenNoUsersExist() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void returnsAllCreatedUsersSortedByName() throws Exception {
        userRepository.save(new User("Zeca", "zeca@farelo.dev", passwordEncoder.encode("senha-forte-123"), UserRole.KITCHEN));
        userRepository.save(new User("Ana", "ana2@farelo.dev", passwordEncoder.encode("senha-forte-123"), UserRole.KITCHEN));

        MvcResult result = mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn();

        List<UserResponse> users = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, UserResponse.class));

        assertThat(users)
                .extracting(UserResponse::name)
                .containsExactly("Ana", "Zeca");
    }

    @Test
    void findsUserById() throws Exception {
        User user = userRepository.save(
                new User("Carlos", "carlos@farelo.dev", passwordEncoder.encode("senha-forte-123"), UserRole.ADMIN));

        mockMvc.perform(get("/api/v1/users/{id}", user.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.name").value("Carlos"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void returnsUserNotFoundWhenGettingUnknownId() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/users/{id}", missingId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void updatesUserAndPersistsChanges() throws Exception {
        User user = userRepository.save(
                new User("Duda", "duda@farelo.dev", passwordEncoder.encode("senha-forte-123"), UserRole.ATTENDANT));

        String body = """
                {
                  "name": "Duda Alves",
                  "email": "duda.alves@farelo.dev",
                  "role": "MANAGER",
                  "active": false
                }
                """;

        mockMvc.perform(put("/api/v1/users/{id}", user.getId())
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Duda Alves"))
                .andExpect(jsonPath("$.email").value("duda.alves@farelo.dev"))
                .andExpect(jsonPath("$.role").value("MANAGER"))
                .andExpect(jsonPath("$.active").value(false));

        Optional<User> persisted = userRepository.findById(user.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getName()).isEqualTo("Duda Alves");
        assertThat(persisted.get().getEmail()).isEqualTo("duda.alves@farelo.dev");
        assertThat(persisted.get().getRole()).isEqualTo(UserRole.MANAGER);
        assertThat(persisted.get().isActive()).isFalse();
    }

    @Test
    void allowsUpdateThatKeepsTheSameEmail() throws Exception {
        User user = userRepository.save(
                new User("Eva", "eva@farelo.dev", passwordEncoder.encode("senha-forte-123"), UserRole.CASHIER));

        String body = """
                {
                  "name": "Eva Lima",
                  "email": "eva@farelo.dev",
                  "role": "CASHIER",
                  "active": true
                }
                """;

        mockMvc.perform(put("/api/v1/users/{id}", user.getId())
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Eva Lima"));
    }

    @Test
    void returnsConflictWhenUpdatingToAnotherUsersEmail() throws Exception {
        userRepository.save(new User("Fabio", "fabio@farelo.dev", passwordEncoder.encode("senha-forte-123"), UserRole.CASHIER));
        User second = userRepository.save(
                new User("Gil", "gil@farelo.dev", passwordEncoder.encode("senha-forte-123"), UserRole.CASHIER));

        String body = """
                {
                  "name": "Gil",
                  "email": "fabio@farelo.dev",
                  "role": "CASHIER",
                  "active": true
                }
                """;

        mockMvc.perform(put("/api/v1/users/{id}", second.getId())
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void returnsUserNotFoundWhenUpdatingUnknownUser() throws Exception {
        UUID missingId = UUID.randomUUID();

        String body = """
                {
                  "name": "Nome",
                  "email": "novo@farelo.dev",
                  "role": "CASHIER",
                  "active": true
                }
                """;

        mockMvc.perform(put("/api/v1/users/{id}", missingId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void rejectsMissingActiveOnUpdateWithStandardErrorFormat() throws Exception {
        User user = userRepository.save(
                new User("Helio", "helio@farelo.dev", passwordEncoder.encode("senha-forte-123"), UserRole.CASHIER));

        String body = """
                {
                  "name": "Helio",
                  "email": "helio@farelo.dev",
                  "role": "CASHIER"
                }
                """;

        mockMvc.perform(put("/api/v1/users/{id}", user.getId())
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void hashesNewPasswordOnPasswordChange() throws Exception {
        User user = userRepository.save(
                new User("Ivo", "ivo@farelo.dev", passwordEncoder.encode("senha-antiga-123"), UserRole.ATTENDANT));
        String originalHash = user.getPasswordHash();

        mockMvc.perform(patch("/api/v1/users/{id}/password", user.getId())
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword": "senha-nova-456"}
                                """))
                .andExpect(status().isOk());

        Optional<User> persisted = userRepository.findById(user.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getPasswordHash()).isNotEqualTo(originalHash);
        assertThat(persisted.get().getPasswordHash()).isNotEqualTo("senha-nova-456");
        assertThat(passwordEncoder.matches("senha-nova-456", persisted.get().getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("senha-antiga-123", persisted.get().getPasswordHash())).isFalse();
    }

    @Test
    void returnsUserNotFoundWhenChangingPasswordOfUnknownUser() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/users/{id}/password", missingId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword": "senha-nova-456"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void rejectsShortNewPasswordWithStandardErrorFormat() throws Exception {
        User user = userRepository.save(
                new User("Julia", "julia@farelo.dev", passwordEncoder.encode("senha-forte-123"), UserRole.KITCHEN));

        mockMvc.perform(patch("/api/v1/users/{id}/password", user.getId())
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword": "123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // --- FARELO-123: RBAC ------------------------------------------------

    @Test
    void rejectsCreateWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Nome", "email": "sem-token@farelo.dev", "password": "senha-forte-123", "role": "CASHIER"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsCreateWhenCallerRoleIsNotAllowed() throws Exception {
        // MANAGER may list/view users (see class javadoc) but not create
        // one — proves list/getById's widened role set does NOT leak into
        // create()'s (still ADMIN-only, method-level, no union).
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Nome", "email": "manager-tentando@farelo.dev", "password": "senha-forte-123", "role": "CASHIER"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsListWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsListWhenCallerRoleIsNotAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void allowsListAsManager() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.MANAGER)))
                .andExpect(status().isOk());
    }

    @Test
    void allowsGetByIdAsManager() throws Exception {
        User user = userRepository.save(
                new User("Karin", "karin@farelo.dev", passwordEncoder.encode("senha-forte-123"), UserRole.ATTENDANT));

        mockMvc.perform(get("/api/v1/users/{id}", user.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Karin"));
    }

    @Test
    void rejectsUpdateWithNoAuthorizationHeader() throws Exception {
        User user = userRepository.save(
                new User("Nilo", "nilo@farelo.dev", passwordEncoder.encode("senha-forte-123"), UserRole.ATTENDANT));

        String body = """
                {
                  "name": "Nilo",
                  "email": "nilo@farelo.dev",
                  "role": "ATTENDANT",
                  "active": true
                }
                """;

        mockMvc.perform(put("/api/v1/users/{id}", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsUpdateWhenCallerRoleIsNotAllowed() throws Exception {
        // MANAGER may view this user but not edit it — update() (unlike
        // list()/getById()) stays ADMIN-only precisely because it can set
        // `role`; see the class javadoc's privilege-escalation reasoning.
        User user = userRepository.save(
                new User("Otto", "otto@farelo.dev", passwordEncoder.encode("senha-forte-123"), UserRole.ATTENDANT));

        String body = """
                {
                  "name": "Otto",
                  "email": "otto@farelo.dev",
                  "role": "ADMIN",
                  "active": true
                }
                """;

        mockMvc.perform(put("/api/v1/users/{id}", user.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsPasswordChangeWithNoAuthorizationHeader() throws Exception {
        User user = userRepository.save(
                new User("Paula", "paula@farelo.dev", passwordEncoder.encode("senha-forte-123"), UserRole.ATTENDANT));

        mockMvc.perform(patch("/api/v1/users/{id}/password", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword": "senha-nova-456"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsPasswordChangeWhenCallerRoleIsNotAllowed() throws Exception {
        User user = userRepository.save(
                new User("Rui", "rui@farelo.dev", passwordEncoder.encode("senha-forte-123"), UserRole.ATTENDANT));

        mockMvc.perform(patch("/api/v1/users/{id}/password", user.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword": "senha-nova-456"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

}
