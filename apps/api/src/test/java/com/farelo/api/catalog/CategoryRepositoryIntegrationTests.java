package com.farelo.api.catalog;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link Category} maps correctly onto the table created by
 * {@code V2__create_category_table.sql}, against a real PostgreSQL instance.
 */
@SpringBootTest
class CategoryRepositoryIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void savesAndFindsCategory() {
        Category category = new Category("Bebidas");

        Category saved = categoryRepository.saveAndFlush(category);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<Category> found = categoryRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Bebidas");
        assertThat(found.get().isActive()).isTrue();
    }

}
