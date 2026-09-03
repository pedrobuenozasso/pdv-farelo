package com.farelo.api.inventory.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.audit.AuditLog;
import com.farelo.api.audit.AuditLogRepository;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.inventory.Ingredient;
import com.farelo.api.inventory.IngredientRepository;
import com.farelo.api.inventory.IngredientUnit;
import com.farelo.api.inventory.InventoryMovement;
import com.farelo.api.inventory.InventoryMovementRepository;
import com.farelo.api.inventory.InventoryMovementType;
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderRepository;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.JwtTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code POST}/{@code GET
 * /api/v1/ingredients/{ingredientId}/movements}, {@code GET
 * /api/v1/ingredients/{ingredientId}/balance}, and {@code POST
 * /api/v1/ingredients/{ingredientId}/losses}, against a real PostgreSQL
 * instance (Testcontainers). {@code POST .../movements} is FARELO-094
 * ("Criar entrada manual de estoque"); {@code GET .../movements} is
 * FARELO-093; {@code GET .../balance} is FARELO-095 ("Calcular saldo do
 * ingrediente"); {@code POST .../losses} is FARELO-098 ("Criar movimento de
 * perda") (see the class javadoc history above/git log for that original
 * scope).
 *
 * <p>No {@code @BeforeEach} table cleanup — same reasoning as {@code
 * RecipeItemControllerIntegrationTests}: every test creates its own fresh
 * ingredient and only asserts on data scoped to that specific ingredient's
 * id, so leftover rows from other test classes never affect an assertion
 * here.
 *
 * <p><b>FARELO-127</b>: {@code POST .../movements}/{@code POST .../losses}
 * now require {@link UserRole#ADMIN}/{@link UserRole#MANAGER} (see {@code
 * InventoryMovementController}'s javadoc), so every {@code POST} here mints
 * a real token via {@link #tokenFor}/{@link #userAndTokenFor} and sends it
 * as {@code Authorization: Bearer <token>} — same pattern {@code
 * ProductControllerIntegrationTests} established for FARELO-123/126. {@code
 * GET .../movements}/{@code GET .../balance} are deliberately left with
 * <b>no</b> header anywhere in this class — see the controller javadoc for
 * why they stay unprotected. The {@code *RecordsAuditLog*}/{@code
 * *401*}/{@code *403*} tests below are new for FARELO-127; every
 * pre-existing {@code POST .../movements}/{@code POST .../losses} test was
 * updated in place to send a token, without otherwise changing what it
 * asserts.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InventoryMovementControllerIntegrationTests extends AbstractIntegrationTest {

    // Command #31 from the FARELO-031 seed — distinct from
    // InventoryMovementRepositoryIntegrationTests' #30 and every other
    // command number already spoken for in this suite.
    private static final int SEEDED_COMMAND_NUMBER = 31;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InventoryMovementRepository inventoryMovementRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private CommandRepository commandRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private static final String PASSWORD = "senha-forte-123";

    // FARELO-127: same tokenFor(UserRole)/userAndTokenFor(UserRole, String)
    // pair ProductControllerIntegrationTests established for FARELO-123/126
    // — tokenFor() is enough for tests that only need *a* valid caller;
    // userAndTokenFor() also hands back the persisted User itself, needed by
    // the audit tests below to assert the AuditLog row's userName/userEmail
    // snapshot actually matches the caller who made the request.
    private String tokenFor(UserRole role) {
        User user = userRepository.save(new User(
                "Test User",
                "test-%s@farelo.dev".formatted(UUID.randomUUID()),
                passwordEncoder.encode(PASSWORD),
                role));
        return jwtTokenService.issue(user).token();
    }

    private record AuthenticatedTestUser(User user, String token) {
    }

    private AuthenticatedTestUser userAndTokenFor(UserRole role, String name) {
        User user = userRepository.save(new User(
                name,
                "test-%s@farelo.dev".formatted(UUID.randomUUID()),
                passwordEncoder.encode(PASSWORD),
                role));
        return new AuthenticatedTestUser(user, jwtTokenService.issue(user).token());
    }

    private Ingredient createIngredient(String name, IngredientUnit unit) {
        return ingredientRepository.save(new Ingredient(name, unit));
    }

    // FARELO-099 ("Criar estoque mínimo"): same as createIngredient() above,
    // but with a minimumStock threshold configured from the start — the
    // two-argument Ingredient constructor always leaves minimumStock null
    // (see its javadoc), so tests that need a configured threshold set it
    // explicitly via the setter before saving.
    private Ingredient createIngredient(String name, IngredientUnit unit, BigDecimal minimumStock) {
        Ingredient ingredient = new Ingredient(name, unit);
        ingredient.setMinimumStock(minimumStock);
        return ingredientRepository.save(ingredient);
    }

    // order_id carries a real DB-level FK to orders(id) — a random UUID is
    // rejected by that constraint, so a real persisted Order is needed (see
    // InventoryMovementRepositoryIntegrationTests#createOrder).
    private Order createOrder() {
        Command command = commandRepository.findByNumber(SEEDED_COMMAND_NUMBER).orElseThrow();
        return orderRepository.save(new Order(command));
    }

    @Test
    void createsPurchaseMovementAndPersistsIt() throws Exception {
        Ingredient beans = createIngredient("Feijão", IngredientUnit.GRAM);

        MvcResult result = mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/movements", beans.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 3000}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.ingredientId").value(beans.getId().toString()))
                .andExpect(jsonPath("$.quantity").value(3000))
                .andExpect(jsonPath("$.type").value("PURCHASE"))
                .andExpect(jsonPath("$.orderId").value(nullValue()))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn();

        InventoryMovementResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), InventoryMovementResponse.class);

        Optional<InventoryMovement> persisted = inventoryMovementRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getType()).isEqualTo(InventoryMovementType.PURCHASE);
        assertThat(persisted.get().getIngredient().getId()).isEqualTo(beans.getId());
        assertThat(persisted.get().getQuantity()).isEqualByComparingTo("3000.000");
    }

    @Test
    void returnsIngredientNotFoundWhenCreatingMovementForUnknownIngredient() throws Exception {
        UUID missingIngredientId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/movements", missingIngredientId)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 100}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsNonPositiveQuantityWithStandardErrorFormat() throws Exception {
        Ingredient rice = createIngredient("Arroz", IngredientUnit.GRAM);

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/movements", rice.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());

        assertThat(inventoryMovementRepository.findByIngredientIdOrderByCreatedAtAsc(rice.getId())).isEmpty();
    }

    @Test
    void rejectsNegativeQuantityWithStandardErrorFormat() throws Exception {
        Ingredient salt = createIngredient("Sal", IngredientUnit.GRAM);

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/movements", salt.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": -50}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsMissingQuantityWithStandardErrorFormat() throws Exception {
        Ingredient pepper = createIngredient("Pimenta", IngredientUnit.GRAM);

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/movements", pepper.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void returnsEmptyListWhenIngredientHasNoMovements() throws Exception {
        Ingredient ingredient = createIngredient("Canela", IngredientUnit.GRAM);

        mockMvc.perform(get("/api/v1/ingredients/{ingredientId}/movements", ingredient.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void returnsIngredientNotFoundWhenListingMovementsOfUnknownIngredient() throws Exception {
        UUID missingIngredientId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/ingredients/{ingredientId}/movements", missingIngredientId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void listsMovementsOldestFirstScopedToIngredientWithOrderIdWhenPresent() throws Exception {
        Ingredient flour = createIngredient("Farinha de trigo", IngredientUnit.GRAM);
        Ingredient sugar = createIngredient("Açúcar", IngredientUnit.GRAM);
        UUID orderId = createOrder().getId();

        InventoryMovement purchase = inventoryMovementRepository.save(
                new InventoryMovement(flour, new BigDecimal("5000"), InventoryMovementType.PURCHASE));
        InventoryMovement consumption = inventoryMovementRepository.save(new InventoryMovement(
                flour, new BigDecimal("-500"), InventoryMovementType.ORDER_CONSUMPTION, orderId));
        // A movement on a different ingredient must not leak into flour's list.
        inventoryMovementRepository.save(
                new InventoryMovement(sugar, new BigDecimal("2000"), InventoryMovementType.PURCHASE));

        MvcResult result = mockMvc.perform(get("/api/v1/ingredients/{ingredientId}/movements", flour.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(purchase.getId().toString()))
                .andExpect(jsonPath("$[0].type").value("PURCHASE"))
                .andExpect(jsonPath("$[0].quantity").value(5000))
                .andExpect(jsonPath("$[0].orderId").value(nullValue()))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[1].id").value(consumption.getId().toString()))
                .andExpect(jsonPath("$[1].type").value("ORDER_CONSUMPTION"))
                .andExpect(jsonPath("$[1].quantity").value(-500))
                .andExpect(jsonPath("$[1].orderId").value(orderId.toString()))
                .andReturn();

        List<InventoryMovementResponse> movements = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, InventoryMovementResponse.class));

        assertThat(movements).extracting(InventoryMovementResponse::ingredientId)
                .containsOnly(flour.getId());
    }

    @Test
    void returnsZeroBalanceWhenIngredientHasNoMovements() throws Exception {
        Ingredient ingredient = createIngredient("Cardamomo", IngredientUnit.GRAM);

        mockMvc.perform(get("/api/v1/ingredients/{ingredientId}/balance", ingredient.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredientId").value(ingredient.getId().toString()))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.unit").value("GRAM"));
    }

    @Test
    void returnsBalanceAsSumOfMovementsScopedToIngredient() throws Exception {
        Ingredient cocoa = createIngredient("Cacau em pó", IngredientUnit.GRAM);
        Ingredient vanilla = createIngredient("Baunilha", IngredientUnit.MILLILITER);
        UUID orderId = createOrder().getId();

        inventoryMovementRepository.save(
                new InventoryMovement(cocoa, new BigDecimal("2000"), InventoryMovementType.PURCHASE));
        inventoryMovementRepository.save(
                new InventoryMovement(cocoa, new BigDecimal("500"), InventoryMovementType.PURCHASE));
        inventoryMovementRepository.save(new InventoryMovement(
                cocoa, new BigDecimal("-300"), InventoryMovementType.ORDER_CONSUMPTION, orderId));
        // A movement on a different ingredient must not affect cocoa's balance.
        inventoryMovementRepository.save(
                new InventoryMovement(vanilla, new BigDecimal("1000"), InventoryMovementType.PURCHASE));

        // 2000 + 500 - 300 = 2200
        mockMvc.perform(get("/api/v1/ingredients/{ingredientId}/balance", cocoa.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredientId").value(cocoa.getId().toString()))
                .andExpect(jsonPath("$.balance").value(2200))
                .andExpect(jsonPath("$.unit").value("GRAM"));
    }

    @Test
    void returnsIngredientNotFoundWhenGettingBalanceOfUnknownIngredient() throws Exception {
        UUID missingIngredientId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/ingredients/{ingredientId}/balance", missingIngredientId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // --- FARELO-099 ("Criar estoque mínimo") — GET .../balance now also
    // reports belowMinimum, computed from comparing the live balance against
    // Ingredient.minimumStock. ---

    @Test
    void reportsBelowMinimumTrueWhenBalanceIsUnderTheConfiguredThreshold() throws Exception {
        Ingredient coffee = createIngredient(
                "Café em grão (below, FARELO-099)", IngredientUnit.GRAM, new BigDecimal("1000"));

        inventoryMovementRepository.save(
                new InventoryMovement(coffee, new BigDecimal("400"), InventoryMovementType.PURCHASE));

        // balance (400) < minimumStock (1000) -> below.
        mockMvc.perform(get("/api/v1/ingredients/{ingredientId}/balance", coffee.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(400))
                .andExpect(jsonPath("$.belowMinimum").value(true));
    }

    @Test
    void reportsBelowMinimumFalseWhenBalanceExactlyMatchesTheConfiguredThreshold() throws Exception {
        Ingredient coffee = createIngredient(
                "Café em grão (at, FARELO-099)", IngredientUnit.GRAM, new BigDecimal("1000"));

        inventoryMovementRepository.save(
                new InventoryMovement(coffee, new BigDecimal("1000"), InventoryMovementType.PURCHASE));

        // balance (1000) == minimumStock (1000) -> at threshold, not below it.
        mockMvc.perform(get("/api/v1/ingredients/{ingredientId}/balance", coffee.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1000))
                .andExpect(jsonPath("$.belowMinimum").value(false));
    }

    @Test
    void reportsBelowMinimumFalseWhenBalanceIsAboveTheConfiguredThreshold() throws Exception {
        Ingredient coffee = createIngredient(
                "Café em grão (above, FARELO-099)", IngredientUnit.GRAM, new BigDecimal("1000"));

        inventoryMovementRepository.save(
                new InventoryMovement(coffee, new BigDecimal("5000"), InventoryMovementType.PURCHASE));

        // balance (5000) > minimumStock (1000) -> above.
        mockMvc.perform(get("/api/v1/ingredients/{ingredientId}/balance", coffee.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(5000))
                .andExpect(jsonPath("$.belowMinimum").value(false));
    }

    // No threshold configured at all (minimumStock null) must never report
    // belowMinimum true, no matter the balance — including a NEGATIVE
    // balance, reachable per InventoryMovementService#consumeForOrder's "no
    // stock-sufficiency check" design (FARELO-096/097, see that method's
    // javadoc). This is the ticket's own explicit test requirement.
    @Test
    void neverReportsBelowMinimumWhenNoThresholdIsConfiguredEvenWithNegativeBalance() throws Exception {
        Ingredient noThreshold = createIngredient("Ingrediente sem limite (FARELO-099)", IngredientUnit.GRAM);
        UUID orderId = createOrder().getId();

        inventoryMovementRepository.save(
                new InventoryMovement(noThreshold, new BigDecimal("100"), InventoryMovementType.PURCHASE));
        inventoryMovementRepository.save(new InventoryMovement(
                noThreshold, new BigDecimal("-300"), InventoryMovementType.ORDER_CONSUMPTION, orderId));

        // balance = 100 - 300 = -200, and minimumStock is null.
        mockMvc.perform(get("/api/v1/ingredients/{ingredientId}/balance", noThreshold.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(-200))
                .andExpect(jsonPath("$.belowMinimum").value(false));
    }

    @Test
    void returnsZeroBalanceWithBelowMinimumFalseWhenNoThresholdConfiguredAndNoMovements() throws Exception {
        Ingredient ingredient = createIngredient("Erva-doce (FARELO-099)", IngredientUnit.GRAM);

        mockMvc.perform(get("/api/v1/ingredients/{ingredientId}/balance", ingredient.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.belowMinimum").value(false));
    }

    // FARELO-098 — "Criar movimento de perda". POST .../losses takes a
    // POSITIVE quantity (how much was lost) and must persist a LOSS row
    // with that quantity negated, no orderId.
    @Test
    void createsLossMovementWithNegatedQuantityAndNoOrderId() throws Exception {
        Ingredient beans = createIngredient("Feijão (FARELO-098)", IngredientUnit.GRAM);

        MvcResult result = mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/losses", beans.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 250}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.ingredientId").value(beans.getId().toString()))
                .andExpect(jsonPath("$.quantity").value(-250))
                .andExpect(jsonPath("$.type").value("LOSS"))
                .andExpect(jsonPath("$.orderId").value(nullValue()))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn();

        InventoryMovementResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), InventoryMovementResponse.class);

        Optional<InventoryMovement> persisted = inventoryMovementRepository.findById(response.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getType()).isEqualTo(InventoryMovementType.LOSS);
        assertThat(persisted.get().getIngredient().getId()).isEqualTo(beans.getId());
        assertThat(persisted.get().getQuantity()).isEqualByComparingTo("-250.000");
        assertThat(persisted.get().getOrderId()).isNull();
    }

    @Test
    void returnsIngredientNotFoundWhenRecordingLossForUnknownIngredient() throws Exception {
        UUID missingIngredientId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/losses", missingIngredientId)
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 100}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsNonPositiveQuantityWhenRecordingLossWithStandardErrorFormat() throws Exception {
        Ingredient rice = createIngredient("Arroz (FARELO-098)", IngredientUnit.GRAM);

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/losses", rice.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());

        assertThat(inventoryMovementRepository.findByIngredientIdOrderByCreatedAtAsc(rice.getId())).isEmpty();
    }

    @Test
    void rejectsNegativeQuantityWhenRecordingLossWithStandardErrorFormat() throws Exception {
        Ingredient salt = createIngredient("Sal (FARELO-098)", IngredientUnit.GRAM);

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/losses", salt.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": -50}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsMissingQuantityWhenRecordingLossWithStandardErrorFormat() throws Exception {
        Ingredient pepper = createIngredient("Pimenta (FARELO-098)", IngredientUnit.GRAM);

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/losses", pepper.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // Confirms the balance endpoint (FARELO-095) correctly reflects a
    // recorded loss — it must go down, since balance is a plain
    // SUM(quantity) over the ledger and a LOSS row is negative.
    @Test
    void balanceReflectsRecordedLoss() throws Exception {
        Ingredient sugar = createIngredient("Açúcar (FARELO-098 balance)", IngredientUnit.GRAM);

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/movements", sugar.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 5000}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/losses", sugar.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 800}
                                """))
                .andExpect(status().isCreated());

        // 5000 - 800 = 4200
        mockMvc.perform(get("/api/v1/ingredients/{ingredientId}/balance", sugar.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredientId").value(sugar.getId().toString()))
                .andExpect(jsonPath("$.balance").value(4200))
                .andExpect(jsonPath("$.unit").value("GRAM"));
    }

    // --- FARELO-127: RBAC on POST .../movements, POST .../losses ----------

    @Test
    void rejectsCreateMovementWithoutAuthorizationHeader() throws Exception {
        Ingredient beans = createIngredient("Feijão (FARELO-127 401)", IngredientUnit.GRAM);

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/movements", beans.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 100}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsCreateMovementWhenCallerRoleIsNotAllowed() throws Exception {
        Ingredient beans = createIngredient("Feijão (FARELO-127 403)", IngredientUnit.GRAM);

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/movements", beans.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 100}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsRecordLossWithoutAuthorizationHeader() throws Exception {
        Ingredient beans = createIngredient("Feijão (FARELO-127 losses 401)", IngredientUnit.GRAM);

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/losses", beans.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 50}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void rejectsRecordLossWhenCallerRoleIsNotAllowed() throws Exception {
        Ingredient beans = createIngredient("Feijão (FARELO-127 losses 403)", IngredientUnit.GRAM);

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/losses", beans.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ATTENDANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 50}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // Manager, not just Admin, may record purchases/losses — same "back-
    // office staff, not the top role alone" judgment call FARELO-123 made
    // for ProductController/CategoryController (see
    // InventoryMovementService#recordAudit's javadoc for the full role
    // reasoning).
    @Test
    void allowsManagerToCreateMovement() throws Exception {
        Ingredient beans = createIngredient("Feijão (FARELO-127 manager)", IngredientUnit.GRAM);

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/movements", beans.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 100}
                                """))
                .andExpect(status().isCreated());
    }

    // --- FARELO-127: auditing a manual stock adjustment --------------------

    @Test
    void creatingPurchaseMovementRecordsAuditLogWithActorAndSnapshot() throws Exception {
        Ingredient beans = createIngredient("Feijão (FARELO-127 audit purchase)", IngredientUnit.GRAM);
        AuthenticatedTestUser actor = userAndTokenFor(UserRole.ADMIN, "Gerente Ana");

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/movements", beans.getId())
                        .header("Authorization", "Bearer " + actor.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 3000}
                                """))
                .andExpect(status().isCreated());

        List<AuditLog> auditLogs = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "Ingredient", beans.getId());
        assertThat(auditLogs).hasSize(1);

        AuditLog auditLog = auditLogs.get(0);
        assertThat(auditLog.getAction()).isEqualTo("STOCK_PURCHASE_RECORDED");
        assertThat(auditLog.getUserId()).isEqualTo(actor.user().getId());
        assertThat(auditLog.getUserName()).isEqualTo("Gerente Ana");
        assertThat(auditLog.getUserEmail()).isEqualTo(actor.user().getEmail());
        assertThat(auditLog.getPreviousValue()).isNull();

        JsonNode newValue = objectMapper.readTree(auditLog.getNewValue());
        assertThat(newValue.get("type").asText()).isEqualTo("PURCHASE");
        assertThat(new BigDecimal(newValue.get("quantity").asText())).isEqualByComparingTo("3000");
    }

    @Test
    void recordingLossRecordsAuditLogWithActorAndSnapshot() throws Exception {
        Ingredient beans = createIngredient("Feijão (FARELO-127 audit loss)", IngredientUnit.GRAM);
        AuthenticatedTestUser actor = userAndTokenFor(UserRole.MANAGER, "Gerente Bruno");

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/losses", beans.getId())
                        .header("Authorization", "Bearer " + actor.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 250}
                                """))
                .andExpect(status().isCreated());

        List<AuditLog> auditLogs = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "Ingredient", beans.getId());
        assertThat(auditLogs).hasSize(1);

        AuditLog auditLog = auditLogs.get(0);
        assertThat(auditLog.getAction()).isEqualTo("STOCK_LOSS_RECORDED");
        assertThat(auditLog.getUserId()).isEqualTo(actor.user().getId());
        assertThat(auditLog.getUserName()).isEqualTo("Gerente Bruno");
        assertThat(auditLog.getPreviousValue()).isNull();

        JsonNode newValue = objectMapper.readTree(auditLog.getNewValue());
        assertThat(newValue.get("type").asText()).isEqualTo("LOSS");
        assertThat(new BigDecimal(newValue.get("quantity").asText())).isEqualByComparingTo("-250");
    }

    @Test
    void auditLogForStockAdjustmentIsQueryableViaAuditLogsEndpoint() throws Exception {
        Ingredient beans = createIngredient("Feijão (FARELO-127 audit query)", IngredientUnit.GRAM);

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/movements", beans.getId())
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 500}
                                """))
                .andExpect(status().isCreated());

        // GET /api/v1/audit-logs stays unprotected at its own first ticket
        // (FARELO-125) — no Authorization header here on purpose, same as
        // every other test exercising that endpoint.
        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("entityType", "Ingredient")
                        .param("entityId", beans.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].action").value("STOCK_PURCHASE_RECORDED"))
                .andExpect(jsonPath("$[0].entityType").value("Ingredient"))
                .andExpect(jsonPath("$[0].entityId").value(beans.getId().toString()));
    }

}
