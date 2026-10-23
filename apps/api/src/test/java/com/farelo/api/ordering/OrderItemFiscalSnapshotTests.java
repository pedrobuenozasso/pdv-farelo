package com.farelo.api.ordering;

import com.farelo.api.catalog.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderItemFiscalSnapshotTests {

    @Test
    void productFiscalIdentityIsFrozenAtSaleCreation() {
        Product product = new Product("Café Espresso", new BigDecimal("7.50"), null);
        product.setGtin("SEM GTIN");
        product.setFiscalUnit("UN");

        OrderItem sold = new OrderItem(null, product, 2, new BigDecimal("7.50"));
        product.setName("Espresso renomeado");
        product.setGtin(null);
        product.setFiscalUnit("CX");

        assertThat(sold.getProductNameSnapshot()).isEqualTo("Café Espresso");
        assertThat(sold.getProductGtinSnapshot()).isEqualTo("SEM GTIN");
        assertThat(sold.getFiscalUnitSnapshot()).isEqualTo("UN");
    }
}
