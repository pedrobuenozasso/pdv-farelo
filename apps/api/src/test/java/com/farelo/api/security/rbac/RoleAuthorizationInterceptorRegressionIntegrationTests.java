package com.farelo.api.security.rbac;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.command.CommandStatus;
import com.farelo.api.inventory.Ingredient;
import com.farelo.api.inventory.IngredientRepository;
import com.farelo.api.inventory.IngredientUnit;
import com.farelo.api.ordering.OrderItemRepository;
import com.farelo.api.ordering.OrderRepository;
import com.farelo.api.ordering.OrderStatusHistoryRepository;
import com.farelo.api.outbox.OutboxWorker;
import com.farelo.api.printing.PrintJobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
 *
 * <h2>FARELO-123 additions</h2>
 *
 * This ticket, unlike FARELO-122, deliberately DOES annotate two of the
 * controllers exercised here — {@code CategoryController} and
 * {@code ProductController} — but only their write methods (see those
 * classes' javadocs). {@link #categoriesListingStillWorksWithNoAuthorizationHeader()}
 * and {@link #productsListingStillWorksWithNoAuthorizationHeader()} now
 * double as the regression proof for that half of FARELO-123's own design:
 * {@code GET} stays public (the customer-facing "Cardápio QR" dependency)
 * even though {@code POST}/{@code PUT} on the very same controllers now
 * require a token.
 *
 * <h2>FARELO-124 — commands/orders/print-jobs</h2>
 *
 * Unlike FARELO-123, this ticket DOES reach into {@code command},
 * {@code ordering} and {@code printing} — so the old
 * {@code ordersListingStillWorksWithNoAuthorizationHeader} test (proving
 * {@code GET /api/v1/orders} needed no token) no longer holds: that
 * endpoint is now {@code ADMIN}/{@code MANAGER}/{@code KITCHEN}-only (see
 * {@code OrderController}'s javadoc) and has been replaced below by
 * {@link #kitchenQueueNowRequiresAuthentication()}, proving the new
 * boundary instead of the old absence of one. What this class still proves
 * — the actual point of a regression suite — is that FARELO-124's blast
 * radius stopped exactly where the ticket intended: the public "Cardápio
 * QR" dependencies ({@link #commandLookupStillWorksWithNoAuthorizationHeader()},
 * {@link #orderCreationStillWorksWithNoAuthorizationHeader()}) and the Edge
 * Agent machine endpoints ({@link #pendingPrintJobsStillWorkWithNoAuthorizationHeader()},
 * {@link #markPrintedStillWorksWithNoAuthorizationHeader()},
 * {@link #markFailedStillWorksWithNoAuthorizationHeader()}) still work with
 * no token at all, exactly as documented in {@code CommandController}/
 * {@code OrderController}/{@code PrintJobController}'s javadocs.
 *
 * <h2>FARELO-127 — the first (and only) two {@code inventory} write
 * endpoints to gain {@code @RequireRole}</h2>
 *
 * Unlike FARELO-122/123/124, this ticket reaches into {@code inventory} —
 * but narrowly: only {@code InventoryMovementController#create} ({@code
 * POST .../movements}) and {@code #recordLoss} ({@code POST .../losses})
 * (see that controller's javadoc, and {@code
 * InventoryMovementService#recordAudit}'s javadoc, for the full "why RBAC
 * here" writeup). {@link #stockPurchaseCreationNowRequiresAuthentication()}/
 * {@link #stockLossRecordingNowRequiresAuthentication()} prove the new
 * boundary; {@link #inventoryMovementListingStillWorksWithNoAuthorizationHeader()},
 * {@link #inventoryMovementBalanceStillWorksWithNoAuthorizationHeader()},
 * {@link #ingredientsListingStillWorksWithNoAuthorizationHeader()} (already
 * present since FARELO-122), {@link
 * #ingredientCreationStillWorksWithNoAuthorizationHeader()} and {@link
 * #ingredientUpdateStillWorksWithNoAuthorizationHeader()} prove this
 * ticket's blast radius stopped exactly there: the rest of {@code
 * InventoryMovementController} and the entirety of {@code
 * IngredientController} remain exactly as unprotected as before this
 * ticket ({@code RecipeController} was never touched at all, by this ticket
 * or any earlier one).
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoleAuthorizationInterceptorRegressionIntegrationTests extends AbstractIntegrationTest {

    // Dedicated seeded command number for this class's orderCreation... test
    // below — distinct from every number already spoken for elsewhere (see
    // CommandControllerIntegrationTests: 1-7, 91-92, 999;
    // CommandRepositoryIntegrationTests: 101, deleted in its own @AfterEach
    // so it doesn't reliably exist outside that one test;
    // OrderControllerIntegrationTests: 10-12; and the rest of the numbers
    // enumerated across the inventory/notification/outbox/printing test
    // classes, all below 45). Reset back to AVAILABLE in @AfterEach, same
    // "mutates shared state, so clean up after yourself" convention as
    // CommandControllerIntegrationTests.
    private static final int COMMAND_NUMBER = 95;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandRepository commandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private PrintJobRepository printJobRepository;

    @Autowired
    private OutboxWorker outboxWorker;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IngredientRepository ingredientRepository;

    // orderCreationStillWorksWithNoAuthorizationHeader() below creates a
    // Category/Product/Order/OrderItem (and, once its OrderCreated outbox
    // event is drained, a PrintJob) — all cleaned up here so nothing
    // lingers to trip a FK violation in some other test class sharing the
    // singleton Postgres container (see AbstractIntegrationTest): a
    // leftover product would break e.g. ProductControllerIntegrationTests'
    // own `productRepository.deleteAll()` @BeforeEach, and a leftover
    // PENDING outbox event would get swept up (and fail, "order not
    // found") by any later test's own `outboxWorker.processPendingEvents()`
    // call, since that method processes every pending event in the shared
    // table, not just the caller's own. Same "mutates shared state, so
    // clean up after yourself" convention as
    // PrintJobControllerIntegrationTests' own @AfterEach, which this
    // mirrors closely.
    private UUID createdOrderId;
    private UUID createdProductId;
    private UUID createdCategoryId;

    @AfterEach
    void resetTestCommand() {
        commandRepository.findByNumber(COMMAND_NUMBER).ifPresent(command -> {
            command.setStatus(CommandStatus.AVAILABLE);
            commandRepository.save(command);
        });

        if (createdOrderId != null) {
            orderRepository.findById(createdOrderId).ifPresent(order -> {
                // Must go first: print_job.order_id is a NOT NULL FK to
                // orders(id).
                printJobRepository.findByOrder(order).forEach(printJobRepository::delete);
                orderStatusHistoryRepository.findByOrderOrderByChangedAtAsc(order)
                        .forEach(orderStatusHistoryRepository::delete);
                orderItemRepository.findByOrder(order).forEach(orderItemRepository::delete);
                orderRepository.delete(order);
            });
            createdOrderId = null;
        }
        if (createdProductId != null) {
            productRepository.deleteById(createdProductId);
            createdProductId = null;
        }
        if (createdCategoryId != null) {
            categoryRepository.deleteById(createdCategoryId);
            createdCategoryId = null;
        }
    }

    @Test
    void categoriesListingStillWorksWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk());
    }

    // FARELO-123: CategoryController now carries @RequireRole on create(),
    // but deliberately not on list() — see that controller's javadoc. This
    // is the test that would fail if that boundary were ever accidentally
    // widened to the whole class.
    @Test
    void productsListingStillWorksWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk());
    }

    @Test
    void ingredientsListingStillWorksWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/ingredients"))
                .andExpect(status().isOk());
    }

    // FARELO-127: IngredientController itself is still completely untouched
    // by this ticket — only two InventoryMovementController write endpoints
    // gained @RequireRole (see this class's own javadoc, "FARELO-127"
    // section). This is the test that would fail if that boundary were ever
    // accidentally widened to IngredientController's own writes.
    @Test
    void ingredientCreationStillWorksWithNoAuthorizationHeader() throws Exception {
        String body = """
                {
                  "name": "Ingrediente Regressão FARELO-127",
                  "unit": "GRAM"
                }
                """;

        mockMvc.perform(post("/api/v1/ingredients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void ingredientUpdateStillWorksWithNoAuthorizationHeader() throws Exception {
        Ingredient ingredient = ingredientRepository.save(
                new Ingredient("Ingrediente Regressão FARELO-127 (update)", IngredientUnit.GRAM));

        String body = """
                {
                  "name": "Ingrediente Regressão FARELO-127 (renomeado)",
                  "unit": "GRAM",
                  "active": true
                }
                """;

        mockMvc.perform(put("/api/v1/ingredients/{id}", ingredient.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // FARELO-127: GET .../movements/GET .../balance on the very same
    // controller as the two now-protected writes stay deliberately
    // unannotated — see InventoryMovementController's javadoc.
    @Test
    void inventoryMovementListingStillWorksWithNoAuthorizationHeader() throws Exception {
        Ingredient ingredient = ingredientRepository.save(
                new Ingredient("Ingrediente Regressão FARELO-127 (movements)", IngredientUnit.GRAM));

        mockMvc.perform(get("/api/v1/ingredients/{ingredientId}/movements", ingredient.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void inventoryMovementBalanceStillWorksWithNoAuthorizationHeader() throws Exception {
        Ingredient ingredient = ingredientRepository.save(
                new Ingredient("Ingrediente Regressão FARELO-127 (balance)", IngredientUnit.GRAM));

        mockMvc.perform(get("/api/v1/ingredients/{ingredientId}/balance", ingredient.getId()))
                .andExpect(status().isOk());
    }

    // FARELO-127: the new boundary itself — POST .../movements now requires
    // a token, unlike every other endpoint proven "still public" above/below.
    @Test
    void stockPurchaseCreationNowRequiresAuthentication() throws Exception {
        Ingredient ingredient = ingredientRepository.save(
                new Ingredient("Ingrediente Regressão FARELO-127 (purchase 401)", IngredientUnit.GRAM));

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/movements", ingredient.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 100}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void stockLossRecordingNowRequiresAuthentication() throws Exception {
        Ingredient ingredient = ingredientRepository.save(
                new Ingredient("Ingrediente Regressão FARELO-127 (loss 401)", IngredientUnit.GRAM));

        mockMvc.perform(post("/api/v1/ingredients/{ingredientId}/losses", ingredient.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 50}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void notificationsListingStillWorksWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk());
    }

    // FARELO-124: CommandController#findByNumber is deliberately left
    // WITHOUT @RequireRole — the "Cardápio QR" customer flow depends on it
    // directly (see that method's javadoc). Command number 1 is the same
    // seeded/read-only number CommandControllerIntegrationTests' own GET
    // tests already read, never mutated by any test — safe to read here
    // too.
    @Test
    void commandLookupStillWorksWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/commands/{number}", 1))
                .andExpect(status().isOk());
    }

    // FARELO-124: OrderController#create is deliberately left WITHOUT
    // @RequireRole — it's the endpoint the "Cardápio QR" checkout flow
    // posts to directly, with no login of any kind (see that method's
    // javadoc). Uses COMMAND_NUMBER (95), this class's own dedicated seeded
    // number.
    @Test
    void orderCreationStillWorksWithNoAuthorizationHeader() throws Exception {
        Category category = categoryRepository.save(new Category("Regressão FARELO-124"));
        Product product = productRepository.save(new Product("Item de Teste", new BigDecimal("5.00"), category));
        createdCategoryId = category.getId();
        createdProductId = product.getId();

        String body = """
                {
                  "commandNumber": %d,
                  "items": [{"productId": "%s", "quantity": 1}]
                }
                """.formatted(COMMAND_NUMBER, product.getId());

        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode responseBody = objectMapper.readTree(result.getResponse().getContentAsString());
        createdOrderId = UUID.fromString(responseBody.get("id").asText());

        // Drains this order's OrderCreated outbox event immediately (same
        // pattern as PrintJobControllerIntegrationTests'
        // createOrderWithPendingPrintJobs) — otherwise it stays PENDING in
        // the table shared by every test class, and a later, unrelated
        // test's own processPendingEvents() call would try to process it
        // too, failing once @AfterEach below has already deleted this
        // order. Draining it here also means the resulting PrintJob (see
        // @AfterEach) is cleaned up deterministically.
        outboxWorker.processPendingEvents();
    }

    // FARELO-124: the kitchen queue now requires ADMIN/MANAGER/KITCHEN —
    // replaces the old FARELO-123-era
    // "ordersListingStillWorksWithNoAuthorizationHeader" test, which proved
    // the opposite (no token needed) back when `ordering` was still
    // entirely out of this ticket's scope. See OrderController's javadoc.
    @Test
    void kitchenQueueNowRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // FARELO-124: PrintJobController#pending/markPrinted/markFailed are
    // deliberately left WITHOUT @RequireRole — called exclusively by the
    // Farelo Edge Agent, a machine with no user login (see
    // PrintJobController's javadoc). Only #retry, a human-triggered
    // action, requires a role.
    @Test
    void pendingPrintJobsStillWorkWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/print-jobs"))
                .andExpect(status().isOk());
    }

    @Test
    void markPrintedStillWorksWithNoAuthorizationHeader() throws Exception {
        // Unknown id -> 404, not 401/403 — proves the request reached the
        // handler (and therefore skipped RBAC entirely) rather than merely
        // returning "some 4xx".
        mockMvc.perform(post("/api/v1/print-jobs/{id}/printed", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRINT_JOB_NOT_FOUND"));
    }

    @Test
    void markFailedStillWorksWithNoAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/print-jobs/{id}/failed", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRINT_JOB_NOT_FOUND"));
    }

}
