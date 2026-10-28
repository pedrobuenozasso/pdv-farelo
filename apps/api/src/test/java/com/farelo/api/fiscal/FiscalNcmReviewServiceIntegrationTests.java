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
                    INSERT INTO fiscal_ncm_review (product_id, source_ncm, draft_ncm,
                                                   source_cest, draft_cest, source_cfop, draft_cfop,
                                                   source_csosn, draft_csosn)
                    VALUES (?, '21069090', '21069090', '1709700', '1709700',
                            '5102', '5102', '102', '102')
                    """, product.getId());

            var review = reviews.save(product.getId(), "19059090", true);

            assertThat(review.sourceNcm()).isEqualTo("21069090");
            assertThat(review.draftNcm()).isEqualTo("19059090");
            assertThat(review.reviewed()).isTrue();
            var taxReview = reviews.saveTaxCodes(product.getId(), null, "5405", "500");
            assertThat(taxReview.sourceCest()).isEqualTo("1709700");
            assertThat(taxReview.draftCest()).isNull();
            assertThat(taxReview.sourceCfop()).isEqualTo("5102");
            assertThat(taxReview.draftCfop()).isEqualTo("5405");
            assertThat(taxReview.sourceCsosn()).isEqualTo("102");
            assertThat(taxReview.draftCsosn()).isEqualTo("500");
            assertThat(taxReview.draftNcm()).isEqualTo("19059090");
            assertThat(taxReview.reviewed()).isTrue();
            assertThat(products.findById(product.getId()).orElseThrow().getFiscalProfile()).isNull();
        } finally {
            products.deleteById(product.getId());
            categories.deleteById(category.getId());
        }
    }
}
