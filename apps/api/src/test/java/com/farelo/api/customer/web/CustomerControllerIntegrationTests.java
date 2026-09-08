package com.farelo.api.customer.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.customer.Customer;
import com.farelo.api.customer.CustomerRepository;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.JwtTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code POST}/{@code GET /api/v1/customers}
 * (FARELO-313), against a real PostgreSQL instance (Testcontainers).
 *
 * <p>{@link AbstractIntegrationTest} shares a single PostgreSQL container
 * across every test class in the run (see that class's javadoc), so each
 * test here clears the {@code customer} table first for a deterministic
 * starting point.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CustomerControllerIntegrationTests extends AbstractIntegrationTest {

    private static final String PASSWORD = "senha-forte-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanCustomerTable() {
        customerRepository.deleteAll();
    }

    private String tokenFor(UserRole role) {
        User user = userRepository.save(new User(
                "Test User",
                "test-%s@farelo.dev".formatted(UUID.randomUUID()),
                passwordEncoder.encode(PASSWORD),
                role));
        return jwtTokenService.issue(user).token();
    }

    @Test
    void createsCustomerAndPersistsIt() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Maria Souza", "phone": "(31) 99876-5432"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Maria Souza"))
                .andExpect(jsonPath("$.phone").value("5531998765432"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn();

        CustomerResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), CustomerResponse.class);

        Optional<Customer> persisted = customerRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getName()).isEqualTo("Maria Souza");
        assertThat(persisted.get().getPhone()).isEqualTo("5531998765432");
    }

    @Test
    void createsCustomerWithNoPhone() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "João"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("João"))
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    @Test
    void rejectsCreateWithBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsCreateWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Maria Souza"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // KITCHEN has no business registering customers, same reasoning as
    // CommandController's role checks.
    @Test
    void rejectsCreateWhenCallerRoleIsNotAllowed() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.KITCHEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Maria Souza"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void searchRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void searchWithoutQueryReturnsEveryCustomerOrderedByName() throws Exception {
        customerRepository.save(new Customer("Zeca", "5531988887777"));
        customerRepository.save(new Customer("Ana", "5531999998888"));

        MvcResult result = mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn();

        List<CustomerResponse> customers = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, CustomerResponse.class));

        assertThat(customers).extracting(CustomerResponse::name).containsExactly("Ana", "Zeca");
    }

    // FARELO-313's core business rule: search must find a match either by
    // name substring OR phone substring, so a single search box works
    // regardless of what staff typed.
    @Test
    void searchByPartialNameIsCaseInsensitive() throws Exception {
        customerRepository.save(new Customer("Maria Souza", null));
        customerRepository.save(new Customer("Carlos", null));

        MvcResult result = mockMvc.perform(get("/api/v1/customers")
                        .param("query", "mar")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andReturn();

        List<CustomerResponse> customers = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, CustomerResponse.class));

        assertThat(customers).extracting(CustomerResponse::name).containsExactly("Maria Souza");
    }

    @Test
    void searchByPartialPhoneFindsMatch() throws Exception {
        customerRepository.save(new Customer("Maria Souza", "5531998765432"));
        customerRepository.save(new Customer("Carlos", "5531911112222"));

        MvcResult result = mockMvc.perform(get("/api/v1/customers")
                        .param("query", "98765")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andReturn();

        List<CustomerResponse> customers = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, CustomerResponse.class));

        assertThat(customers).extracting(CustomerResponse::name).containsExactly("Maria Souza");
    }

}
