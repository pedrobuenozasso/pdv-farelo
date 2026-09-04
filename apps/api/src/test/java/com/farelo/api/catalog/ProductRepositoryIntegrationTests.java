package com.farelo.api.catalog;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.fiscal.FiscalProfile;
import com.farelo.api.fiscal.FiscalProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link Product} maps correctly onto the table created by
 * {@code V3__create_product_table.sql}, including its FK to {@link Category}
 * and (FARELO-151) its optional FK to {@code FiscalProfile}
 * ({@code V28__add_product_fiscal_profile_id_column.sql}), against a real
 * PostgreSQL instance.
 */
@SpringBootTest
class ProductRepositoryIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private FiscalProfileRepository fiscalProfileRepository;

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
        // fiscalProfile not set above -> stays null (FARELO-151, optional).
        assertThat(found.get().getFiscalProfile()).isNull();
    }

    @Test
    void savesAndFindsProductWithFiscalProfile() {
        Category category = categoryRepository.saveAndFlush(new Category("Bebidas"));
        FiscalProfile fiscalProfile = fiscalProfileRepository.saveAndFlush(new FiscalProfile("Isento"));

        Product product = new Product("Café Espresso", new BigDecimal("7.50"), category);
        product.setFiscalProfile(fiscalProfile);

        Product saved = productRepository.saveAndFlush(product);

        Optional<Product> found = productRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getFiscalProfile()).isNotNull();
        assertThat(found.get().getFiscalProfile().getId()).isEqualTo(fiscalProfile.getId());
    }

}
