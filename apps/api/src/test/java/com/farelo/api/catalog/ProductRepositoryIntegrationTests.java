package com.farelo.api.catalog;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link Product} maps correctly onto the table created by
 * {@code V3__create_product_table.sql}, including its FK to {@link Category},
 * against a real PostgreSQL instance.
 */
@SpringBootTest
class ProductRepositoryIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void savesAndFindsProductWithCategory() {
        Category category = categoryRepository.saveAndFlush(new Category("Bebidas"));

        Product product = new Product("Café Espresso", new BigDecimal("7.50"), category);
        product.setDescription("Espresso curto, torra média");

        Product saved = productRepository.saveAndFlush(product);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<Product> found = productRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Café Espresso");
        // BigDecimal price: compare by value, not by scale (AGENTS.md: money is
        // always BigDecimal, and equals() would fail here since the DB column
        // is NUMERIC(10,2) and may come back with a different scale than the
        // literal used above).
        assertThat(found.get().getPrice()).isEqualByComparingTo(new BigDecimal("7.50"));
        assertThat(found.get().getCategory().getId()).isEqualTo(category.getId());
    }

}
