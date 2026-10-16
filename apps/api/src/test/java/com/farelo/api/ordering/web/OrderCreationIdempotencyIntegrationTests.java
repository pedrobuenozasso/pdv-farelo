package com.farelo.api.ordering.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.inventory.Ingredient;
import com.farelo.api.inventory.IngredientRepository;
import com.farelo.api.inventory.IngredientUnit;
import com.farelo.api.inventory.InventoryMovementRepository;
import com.farelo.api.inventory.Recipe;
import com.farelo.api.inventory.RecipeItem;
import com.farelo.api.inventory.RecipeItemRepository;
import com.farelo.api.inventory.RecipeRepository;
import com.farelo.api.ordering.OrderRepository;
import com.farelo.api.outbox.OutboxEventRepository;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.JwtTokenService;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderCreationIdempotencyIntegrationTests extends AbstractIntegrationTest {
    private static final AtomicInteger NEXT_COMMAND_NUMBER = new AtomicInteger(654);

    @Autowired MockMvc mockMvc;
    @Autowired CommandRepository commandRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired IngredientRepository ingredientRepository;
    @Autowired RecipeRepository recipeRepository;
    @Autowired RecipeItemRepository recipeItemRepository;
    @Autowired InventoryMovementRepository inventoryMovementRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtTokenService jwtTokenService;
    @Autowired ObjectMapper objectMapper;

    private final List<RecipeItem> createdRecipeItems = new ArrayList<>();
    private int commandNumber;

    @BeforeEach
    void setUp() {
        commandNumber = NEXT_COMMAND_NUMBER.getAndIncrement();
        commandRepository.save(new Command(commandNumber));
    }

    @AfterEach
    void cleanup() {
        recipeItemRepository.deleteAll(createdRecipeItems);
    }

    private Product productWithRecipe() {
        Category category = categoryRepository.save(new Category("Idempotência " + UUID.randomUUID()));
        Product product = productRepository.save(new Product("Bolo", new BigDecimal("12.50"), category));
        Ingredient ingredient = ingredientRepository.save(new Ingredient("Farinha " + UUID.randomUUID(), IngredientUnit.GRAM));
        Recipe recipe = recipeRepository.save(new Recipe(product));
        createdRecipeItems.add(recipeItemRepository.save(new RecipeItem(recipe, ingredient, new BigDecimal("10"))));
        return product;
    }

    private String commandBody(Product product, int quantity) {
        return """
                {"commandNumber": %d, "items": [{"productId": "%s", "quantity": %d}]}
                """.formatted(commandNumber, product.getId(), quantity);
    }

    private MvcResult submit(String path, String body, String key, String authorization) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("Idempotency-Key", key);
        if (authorization != null) {
            request.header("Authorization", "Bearer " + authorization);
        }
        return mockMvc.perform(request).andExpect(status().isCreated()).andReturn();
    }

    @Test
    void sameKeyReturnsOriginalSnapshotWithOneOrderInventoryAndOutboxEffect() throws Exception {
        Product product = productWithRecipe();
        String body = commandBody(product, 2);
        String key = UUID.randomUUID().toString();

        MvcResult first = submit("/api/v1/orders", body, key, null);
        UUID orderId = objectMapper.readValue(first.getResponse().getContentAsString(), OrderResponse.class).id();
        product.setPrice(new BigDecimal("19.00"));
        productRepository.save(product);
        MvcResult retry = submit("/api/v1/orders", body, key, null);

        assertThat(retry.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        assertThat(retry.getResponse().getHeader("Location")).isEqualTo(first.getResponse().getHeader("Location"));
        assertThat(retry.getResponse().getHeader("Location")).isEqualTo("/api/v1/orders/" + orderId);
        assertThat(orderRepository.findByCommandOrderByCreatedAtAsc(commandRepository.findByNumber(commandNumber).orElseThrow()))
                .hasSize(1);
        assertThat(inventoryMovementRepository.findByOrderId(orderId)).hasSize(1);
        assertThat(outboxEventRepository.findAll().stream()
                .filter(event -> orderId.equals(event.getAggregateId()) && "OrderCreated".equals(event.getEventType())))
                .hasSize(1);
    }

    @Test
    void concurrentSameKeyCreatesOneOrder() throws Exception {
        Product product = productWithRecipe();
        String body = commandBody(product, 1);
        String key = UUID.randomUUID().toString();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<MvcResult> call = () -> {
                ready.countDown();
                start.await();
                return submit("/api/v1/orders", body, key, null);
            };
            Future<MvcResult> one = pool.submit(call);
            Future<MvcResult> two = pool.submit(call);
            ready.await();
            start.countDown();
            MvcResult first = one.get();
            MvcResult second = two.get();
            assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
            UUID orderId = objectMapper.readValue(first.getResponse().getContentAsString(), OrderResponse.class).id();
            assertThat(orderRepository.findByCommandOrderByCreatedAtAsc(commandRepository.findByNumber(commandNumber).orElseThrow()))
                    .hasSize(1);
            assertThat(inventoryMovementRepository.findByOrderId(orderId)).hasSize(1);
        }
    }

    @Test
    void sameKeyWithDifferentBodyIsConflict() throws Exception {
        Product product = productWithRecipe();
        String key = UUID.randomUUID().toString();
        submit("/api/v1/orders", commandBody(product, 1), key, null);

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandBody(product, 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void omittedAndExplicitTruePrintFlagAreSameEffectiveRequest() throws Exception {
        Product product = productWithRecipe();
        String key = UUID.randomUUID().toString();
        String body = commandBody(product, 1);
        MvcResult first = submit("/api/v1/orders", body, key, null);
        String explicitTrue = body.substring(0, body.lastIndexOf('}')) + ", \"printOnConfirm\": true}";
        MvcResult retry = submit("/api/v1/orders", explicitTrue, key, null);
        assertThat(retry.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
    }

    @Test
    void invalidKeyIsBadRequest() throws Exception {
        Product product = productWithRecipe();
        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandBody(product, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));
    }

    @Test
    void failedCreationRollsBackTheKeySoTheSameIntentCanBeRetried() throws Exception {
        Product product = productWithRecipe();
        int laterCommandNumber = commandNumber + 10000;
        String body = """
                {"commandNumber": %d, "items": [{"productId": "%s", "quantity": 1}]}
                """.formatted(laterCommandNumber, product.getId());
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMAND_NOT_FOUND"));

        commandRepository.save(new Command(laterCommandNumber));
        submit("/api/v1/orders", body, key, null);
    }

    @Test
    void balcaoUsesSameContractAndDoesNotShareKeyWithComanda() throws Exception {
        Product product = productWithRecipe();
        User cashier = userRepository.save(new User("Cashier", "idem-" + UUID.randomUUID() + "@farelo.dev",
                passwordEncoder.encode("test-password"), UserRole.CASHIER));
        String token = jwtTokenService.issue(cashier).token();
        String body = """
                {"items": [{"productId": "%s", "quantity": 1}]}
                """.formatted(product.getId());
        String key = UUID.randomUUID().toString();
        MvcResult first = submit("/api/v1/orders/balcao", body, key, token);
        MvcResult retry = submit("/api/v1/orders/balcao", body, key, token);
        assertThat(retry.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandBody(product, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }
}
