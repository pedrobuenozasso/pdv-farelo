package com.farelo.api.catalog;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ProductStepRepositoryIntegrationTests extends AbstractIntegrationTest {

    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private ProductStepRepository steps;
    @Autowired private ProductStepOptionRepository options;

    @AfterEach
    void cleanSteps() {
        options.deleteAllInBatch();
        steps.deleteAllInBatch();
    }

    private Product product(String name, String price) {
        Category category = categories.saveAndFlush(new Category("Configuráveis"));
        return products.saveAndFlush(new Product(name, new BigDecimal(price), category));
    }

    private ProductStep step(Product parent, int minimum, int maximum, int order) {
        return new ProductStep(parent, "Escolha seu sabor", minimum, maximum, order,
                "Mesmo nivel do produto principal", "Unitário");
    }

    @Test
    void persistsOrderedStepsAndLinkPricesIndependentOfProductPrices() {
        Product parent = product("Cappuccino", "0.00");
        Product cinnamon = product("Canela", "20.00");
        cinnamon.setActive(false);
        products.saveAndFlush(cinnamon);

        ProductStep second = steps.saveAndFlush(step(parent, 0, 1, 1));
        ProductStep first = steps.saveAndFlush(step(parent, 1, 10, 0));
        options.saveAndFlush(new ProductStepOption(first, cinnamon, 0, new BigDecimal("16.00")));
        options.saveAndFlush(new ProductStepOption(second, cinnamon, 0, BigDecimal.ZERO));

        List<ProductStep> found = steps.findByProductIdOrderByDisplayOrderAsc(parent.getId());
        assertThat(found).extracting(ProductStep::getId).containsExactly(first.getId(), second.getId());
        assertThat(found.get(0).getMinChoices()).isEqualTo(1);
        assertThat(found.get(0).getMaxChoices()).isEqualTo(10);
        assertThat(found.get(0).getLevel()).isEqualTo("Mesmo nivel do produto principal");
        assertThat(found.get(0).getChargeType()).isEqualTo("Unitário");

        List<ProductStepOption> firstOptions = options.findByStepIdOrderByDisplayOrderAsc(first.getId());
        assertThat(firstOptions).hasSize(1);
        assertThat(firstOptions.get(0).getOptionProduct().getId()).isEqualTo(cinnamon.getId());
        assertThat(firstOptions.get(0).getPrice()).isEqualByComparingTo("16.00");
        assertThat(firstOptions.get(0).getPrice()).isNotEqualByComparingTo(cinnamon.getPrice());
        assertThat(options.findByStepIdOrderByDisplayOrderAsc(second.getId()).get(0).getPrice())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsInvalidLimitsAndDuplicateStepOrderAtDatabaseLevel() {
        Product parent = product("Picolé", "0.00");
        steps.saveAndFlush(step(parent, 1, 100, 0));

        assertThatThrownBy(() -> steps.saveAndFlush(step(parent, 0, 1, 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsMinimumAboveMaximumAtDatabaseLevel() {
        Product parent = product("Refrigerante", "0.00");
        assertThatThrownBy(() -> steps.saveAndFlush(step(parent, 2, 1, 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateOptionForOneStepAtDatabaseLevel() {
        Product parent = product("Cerveja", "0.00");
        Product option = product("Lata", "15.00");
        ProductStep step = steps.saveAndFlush(step(parent, 1, 1, 0));
        options.saveAndFlush(new ProductStepOption(step, option, 0, new BigDecimal("15.00")));

        assertThatThrownBy(() -> options.saveAndFlush(
                new ProductStepOption(step, option, 1, new BigDecimal("15.00"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNegativeLinkPriceAtDatabaseLevel() {
        Product parent = product("Café", "10.00");
        Product option = product("Curto", "7.00");
        ProductStep step = steps.saveAndFlush(step(parent, 1, 1, 0));

        assertThatThrownBy(() -> options.saveAndFlush(
                new ProductStepOption(step, option, 0, new BigDecimal("-0.01"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
