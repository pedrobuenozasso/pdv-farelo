package com.farelo.api.fiscal;

import com.farelo.api.catalog.Product;
import com.farelo.api.ordering.OrderItem;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NfceOrderItemMapperTests {

    @Test
    void mapsFrozenIdentityAndPriceWithExplicitFiscalProfile() {
        FiscalProfile profile = completeProfile();
        Product product = product(profile);
        OrderItem sold = persisted(new OrderItem(null, product, 2, new BigDecimal("7.50")));
        product.setName("Nome novo");
        product.setGtin(null);
        product.setFiscalUnit("CX");
        product.setPrice(new BigDecimal("9.00"));

        NfceXmlBuilder.Item item = NfceOrderItemMapper.mapSimple(sold, "1");

        assertThat(item.description()).isEqualTo("Café Espresso");
        assertThat(item.gtin()).isEqualTo("SEM GTIN");
        assertThat(item.unit()).isEqualTo("UN");
        assertThat(item.unitPrice()).isEqualByComparingTo("7.50");
        assertThat(item.total()).isEqualByComparingTo("15.00");
        assertThat(item.taxes().icmsCode()).isEqualTo("102");
    }

    @Test
    void rejectsHistoricalLineWithoutFiscalSnapshots() {
        OrderItem sold = persisted(new OrderItem(null, product(completeProfile()), 1, BigDecimal.ONE));
        ReflectionTestUtils.setField(sold, "productNameSnapshot", null);

        assertThatThrownBy(() -> NfceOrderItemMapper.mapSimple(sold, "1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("snapshot");
    }

    @Test
    void rejectsUnsupportedContributionWithoutInventingTaxAmounts() {
        FiscalProfile profile = completeProfile();
        profile.setPisCst("99");
        OrderItem sold = persisted(new OrderItem(null, product(profile), 1, BigDecimal.ONE));

        assertThatThrownBy(() -> NfceOrderItemMapper.mapSimple(sold, "1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CST 99");
    }

    private static OrderItem persisted(OrderItem item) {
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        return item;
    }

    private static Product product(FiscalProfile profile) {
        Product product = new Product("Café Espresso", new BigDecimal("7.50"), null);
        ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
        product.setGtin("SEM GTIN");
        product.setFiscalUnit("UN");
        product.setFiscalProfile(profile);
        return product;
    }

    private static FiscalProfile completeProfile() {
        FiscalProfile profile = new FiscalProfile("Café");
        profile.setNcm("21011110");
        profile.setCfop("5102");
        profile.setCsosn("102");
        profile.setIcmsOrigin("0");
        profile.setPisCst("07");
        profile.setCofinsCst("07");
        return profile;
    }
}
