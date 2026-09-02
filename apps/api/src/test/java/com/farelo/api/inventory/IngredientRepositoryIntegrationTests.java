package com.farelo.api.inventory;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link Ingredient} maps correctly onto the table created by
 * {@code V16__create_ingredient_table.sql}, against a real PostgreSQL
 * instance.
 */
@SpringBootTest
class IngredientRepositoryIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private IngredientRepository ingredientRepository;

    @Test
    void savesAndFindsIngredient() {
        Ingredient ingredient = new Ingredient("Leite", IngredientUnit.MILLILITER);

        Ingredient saved = ingredientRepository.saveAndFlush(ingredient);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<Ingredient> found = ingredientRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Leite");
        assertThat(found.get().getUnit()).isEqualTo(IngredientUnit.MILLILITER);
        assertThat(found.get().isActive()).isTrue();
    }

}
