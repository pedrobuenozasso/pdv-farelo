package com.farelo.api.table.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.command.CommandStatus;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.JwtTokenService;
import com.farelo.api.table.DiningTable;
import com.farelo.api.table.DiningTableRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code POST}/{@code GET /api/v1/tables} and
 * {@code GET /api/v1/tables/occupancy} (FARELO-315), against a real
 * PostgreSQL instance (Testcontainers).
 *
 * <p>{@link AbstractIntegrationTest} shares a single PostgreSQL container
 * across every test class in the run (see that class's javadoc), so each
 * test here clears {@code dining_table} first — and clears any {@code
 * command.table_id} left over from a previous run before that, since the
 * FK would otherwise block the delete.
 *
 * <p>The occupancy tests use dedicated seeded comanda numbers (200/201 —
 * comfortably outside the 1-100 seed range and every other test class's
 * hand-picked numbers, since this class links real seeded/ad-hoc commands
 * to tables and must not collide with another test's own use of those
 * same rows) and reset them in {@code @AfterEach}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DiningTableControllerIntegrationTests extends AbstractIntegrationTest {

    private static final String PASSWORD = "senha-forte-123";
    private static final int OCCUPIED_COMMAND_NUMBER = 6;
    private static final int AVAILABLE_COMMAND_NUMBER = 7;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DiningTableRepository diningTableRepository;

    @Autowired
    private CommandRepository commandRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanTables() {
        clearCommandTableLink(OCCUPIED_COMMAND_NUMBER);
        clearCommandTableLink(AVAILABLE_COMMAND_NUMBER);
        diningTableRepository.deleteAll();
    }

    @AfterEach
    void resetSeededCommands() {
        clearCommandTableLink(OCCUPIED_COMMAND_NUMBER);
        resetToAvailable(AVAILABLE_COMMAND_NUMBER);
        resetToAvailable(OCCUPIED_COMMAND_NUMBER);
    }

    private void clearCommandTableLink(int number) {
        commandRepository.findByNumber(number).ifPresent(command -> {
            command.setTable(null);
            commandRepository.save(command);
        });
    }

    private void resetToAvailable(int number) {
        commandRepository.findByNumber(number).ifPresent(command -> {
            command.setStatus(CommandStatus.AVAILABLE);
            commandRepository.save(command);
        });
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
    void createsTableAndPersistsIt() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"number": 12, "capacity": 4}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.number").value(12))
                .andExpect(jsonPath("$.capacity").value(4))
                .andReturn();

        DiningTableResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), DiningTableResponse.class);

        Optional<DiningTable> persisted = diningTableRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getNumber()).isEqualTo(12);
        assertThat(persisted.get().getCapacity()).isEqualTo(4);
    }

    @Test
    void createAsManagerSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"number": 13, "capacity": 2}
                                """))
                .andExpect(status().isCreated());
    }

    // Table setup is back-office work — CASHIER/ATTENDANT can't do it,
    // same reasoning as CategoryController/ProductController's writes.
    @Test
    void rejectsCreateWhenCallerRoleIsNotAllowed() throws Exception {
        mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"number": 14, "capacity": 2}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void rejectsCreateWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/tables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"number": 15, "capacity": 2}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void rejectsDuplicateTableNumber() throws Exception {
        diningTableRepository.save(new DiningTable(16, 4));

        mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"number": 16, "capacity": 6}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TABLE_NUMBER_ALREADY_EXISTS"));
    }

    @Test
    void rejectsNonPositiveCapacity() throws Exception {
        mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"number": 17, "capacity": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/tables"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void listReturnsTablesOrderedByNumber() throws Exception {
        diningTableRepository.save(new DiningTable(22, 4));
        diningTableRepository.save(new DiningTable(21, 2));

        MvcResult result = mockMvc.perform(get("/api/v1/tables")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn();

        List<DiningTableResponse> tables = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, DiningTableResponse.class));

        assertThat(tables).extracting(DiningTableResponse::number).containsExactly(21, 22);
    }

    // --- FARELO-315: occupancy business rule ------------------------------

    @Test
    void occupancyCountsOnlyDistinctTablesLinkedToActiveCommands() throws Exception {
        DiningTable occupiedTable = diningTableRepository.save(new DiningTable(30, 4));
        diningTableRepository.save(new DiningTable(31, 2)); // free table, no command linked

        Command occupied = commandRepository.findByNumber(OCCUPIED_COMMAND_NUMBER).orElseThrow();
        occupied.setStatus(CommandStatus.OPEN);
        occupied.setTable(occupiedTable);
        commandRepository.save(occupied);

        // A command in AVAILABLE status linked to no table must not count
        // as occupying anything.
        Command available = commandRepository.findByNumber(AVAILABLE_COMMAND_NUMBER).orElseThrow();
        available.setStatus(CommandStatus.AVAILABLE);
        commandRepository.save(available);

        mockMvc.perform(get("/api/v1/tables/occupancy")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupiedTables").value(1))
                .andExpect(jsonPath("$.totalTables").value(2))
                .andExpect(jsonPath("$.occupancyPercentage").value(50.0));
    }

    @Test
    void occupancyIsZeroWhenNoTablesExist() throws Exception {
        mockMvc.perform(get("/api/v1/tables/occupancy")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupiedTables").value(0))
                .andExpect(jsonPath("$.totalTables").value(0))
                .andExpect(jsonPath("$.occupancyPercentage").value(0.0));
    }

    // --- FARELO-315: PATCH /api/v1/commands/{number}/table exercised from
    // this class too, since it needs a real DiningTable row to link to.

    @Test
    void linksAndUnlinksTableOnCommand() throws Exception {
        diningTableRepository.save(new DiningTable(40, 4));

        mockMvc.perform(patch("/api/v1/commands/{number}/table", OCCUPIED_COMMAND_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tableNumber": 40}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableNumber").value(40));

        Command linked = commandRepository.findByNumber(OCCUPIED_COMMAND_NUMBER).orElseThrow();
        assertThat(linked.getTable()).isNotNull();
        assertThat(linked.getTable().getNumber()).isEqualTo(40);

        // omitting tableNumber (full-replace) clears the link
        mockMvc.perform(patch("/api/v1/commands/{number}/table", OCCUPIED_COMMAND_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableNumber").doesNotExist());

        Command unlinked = commandRepository.findByNumber(OCCUPIED_COMMAND_NUMBER).orElseThrow();
        assertThat(unlinked.getTable()).isNull();
    }

    @Test
    void returnsTableNotFoundWhenLinkingUnknownTableNumber() throws Exception {
        mockMvc.perform(patch("/api/v1/commands/{number}/table", AVAILABLE_COMMAND_NUMBER)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tableNumber": 999}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TABLE_NOT_FOUND"));
    }

}
