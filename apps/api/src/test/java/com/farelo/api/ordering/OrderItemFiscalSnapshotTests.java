package com.farelo.api.ordering;

import com.farelo.api.catalog.Product;
import com.farelo.api.fiscal.FiscalProfile;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderItemFiscalSnapshotTests {

    @Test
    void productFiscalIdentityIsFrozenAtSaleCreation() {
        Product product = new Product("Café Espresso", new BigDecimal("7.50"), null);
        product.setGtin("SEM GTIN");
        product.setFiscalUnit("UN");
        FiscalProfile profile = new FiscalProfile("Café");
        profile.setNcm("21011110");
        profile.setCfop("5102");
        profile.setCsosn("102");
        profile.setIcmsOrigin("0");
        profile.setPisCst("07");
        profile.setCofinsCst("07");
        product.setFiscalProfile(profile);

        OrderItem sold = new OrderItem(null, product, 2, new BigDecimal("7.50"));
        product.setName("Espresso renomeado");
        product.setGtin(null);
        product.setFiscalUnit("CX");
        profile.setNcm("21011200");
        product.setFiscalProfile(null);

        assertThat(sold.getProductNameSnapshot()).isEqualTo("Café Espresso");
        assertThat(sold.getProductGtinSnapshot()).isEqualTo("SEM GTIN");
        assertThat(sold.getFiscalUnitSnapshot()).isEqualTo("UN");
        assertThat(sold.getFiscalNcmSnapshot()).isEqualTo("21011110");
        assertThat(sold.getFiscalCfopSnapshot()).isEqualTo("5102");
        assertThat(sold.getFiscalCsosnSnapshot()).isEqualTo("102");
    }
}
