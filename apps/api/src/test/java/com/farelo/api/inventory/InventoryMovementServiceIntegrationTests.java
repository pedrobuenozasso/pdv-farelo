package com.farelo.api.inventory;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@link InventoryMovementService#create(UUID, BigDecimal)}
 * (FARELO-094, "Criar entrada manual de estoque") directly, against a real
 * PostgreSQL instance — the manual stock-entry flow's business logic
 * (ingredient existence check, hardcoded {@code PURCHASE} type, the row
 * actually landing in the ledger) rather than HTTP concerns, which are
 * {@code InventoryMovementControllerIntegrationTests}' job.
 *
 * <p>No {@code @BeforeEach} table cleanup — same reasoning as {@code
 * InventoryMovementRepositoryIntegrationTests}: every test creates its own
 * fresh ingredient and only asserts on movements scoped to that specific
 * ingredient's id, so leftover rows from other test classes sharing the
 * singleton Postgres container never affect an assertion here.
 */
@SpringBootTest
class InventoryMovementServiceIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private InventoryMovementService inventoryMovementService;

    @Autowired
    private InventoryMovementRepository inventoryMovementRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    private Ingredient createIngredient(String name, IngredientUnit unit) {
        return ingredientRepository.save(new Ingredient(name, unit));
    }

    @Test
    void createsPurchaseMovementLinkedToIngredient() {
        Ingredient coffee = createIngredient("Café em grão", IngredientUnit.GRAM);

        InventoryMovement created = inventoryMovementService.create(coffee.getId(), new BigDecimal("2000"));

        assertThat(created.getId()).isNotNull();
        assertThat(created.getType()).isEqualTo(InventoryMovementType.PURCHASE);
        assertThat(created.getQuantity()).isEqualByComparingTo("2000");
        assertThat(created.getIngredient().getId()).isEqualTo(coffee.getId());
        assertThat(created.getOrderId()).isNull();
        assertThat(created.getCreatedAt()).isNotNull();

        Optional<InventoryMovement> persisted = inventoryMovementRepository.findById(created.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getType()).isEqualTo(InventoryMovementType.PURCHASE);
        assertThat(persisted.get().getQuantity()).isEqualByComparingTo("2000.000");
        assertThat(persisted.get().getIngredient().getId()).isEqualTo(coffee.getId());
    }

    @Test
    void throwsIngredientNotFoundWhenCreatingMovementForUnknownIngredient() {
        UUID missingIngredientId = UUID.randomUUID();

        assertThatThrownBy(() -> inventoryMovementService.create(missingIngredientId, BigDecimal.TEN))
                .isInstanceOf(IngredientNotFoundException.class);

        assertThat(inventoryMovementRepository.findByIngredientIdOrderByCreatedAtAsc(missingIngredientId)).isEmpty();
    }

}
