package com.farelo.api.fiscal;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FiscalNcmReviewServiceIntegrationTests extends AbstractIntegrationTest {
    @Autowired private FiscalNcmReviewService reviews;
    @Autowired private ProductRepository products;
    @Autowired private CategoryRepository categories;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void editsDraftWithoutChangingSourceOrEmissionProfile() {
        Category category = categories.save(new Category("Fiscal draft test"));
        Product product = products.save(new Product("Pão com ovo test", new BigDecimal("28.00"), category));
        try {
            jdbc.update("""
                    INSERT INTO fiscal_ncm_review (product_id, source_ncm, draft_ncm)
                    VALUES (?, '21069090', '21069090')
                    """, product.getId());

            var review = reviews.save(product.getId(), "19059090", true);

            assertThat(review.sourceNcm()).isEqualTo("21069090");
            assertThat(review.draftNcm()).isEqualTo("19059090");
            assertThat(review.reviewed()).isTrue();
            assertThat(products.findById(product.getId()).orElseThrow().getFiscalProfile()).isNull();
        } finally {
            products.deleteById(product.getId());
            categories.deleteById(category.getId());
        }
    }
}
