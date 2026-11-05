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
        profile.setNcm("21011200");
        product.setFiscalProfile(null);

        NfceXmlBuilder.Item item = NfceOrderItemMapper.mapSimple(sold, "1");

        assertThat(item.description()).isEqualTo("Café Espresso");
        assertThat(item.gtin()).isEqualTo("SEM GTIN");
        assertThat(item.unit()).isEqualTo("UN");
        assertThat(item.unitPrice()).isEqualByComparingTo("7.50");
        assertThat(item.total()).isEqualByComparingTo("15.00");
        assertThat(item.ncm()).isEqualTo("21011110");
        assertThat(item.taxes().icmsCode()).isEqualTo("102");
    }

    @Test
    void rejectsHistoricalLineWithoutFiscalSnapshots() {
        OrderItem sold = persisted(new OrderItem(null, product(completeProfile()), 1, BigDecimal.ONE));
        ReflectionTestUtils.setField(sold, "productNameSnapshot", null);

        assertThatThrownBy(() -> NfceOrderItemMapper.mapSimple(sold, "1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("immutable fiscal product data");
    }

    @Test
    void simplesNacionalCst99CarriesExplicitZeroAmountsByRegime() {
        FiscalProfile profile = completeProfile();
        profile.setPisCst("99");
        profile.setCofinsCst("99");
        OrderItem sold = persisted(new OrderItem(null, product(profile), 1, BigDecimal.ONE));

        NfceXmlBuilder.Item item = NfceOrderItemMapper.mapSimple(sold, "1");

        for (NfceXmlBuilder.OtherContribution other
                : java.util.List.of(item.taxes().pisOther(), item.taxes().cofinsOther())) {
            assertThat(other).isNotNull();
            // Same "explicit zero" NfceXmlBuilder demands for CST 99: value 0, scale <= 2.
            assertThat(other.base()).isEqualByComparingTo("0").satisfies(v -> assertThat(v.scale()).isLessThanOrEqualTo(2));
            assertThat(other.rate()).isEqualByComparingTo("0").satisfies(v -> assertThat(v.scale()).isLessThanOrEqualTo(2));
            assertThat(other.amount()).isEqualByComparingTo("0").satisfies(v -> assertThat(v.scale()).isLessThanOrEqualTo(2));
        }
    }

    @Test
    void nonSimplesCst99StillRefusesToInventTaxAmounts() {
        FiscalProfile profile = completeProfile();
        profile.setCsosn(null);
        profile.setCst("40");
        profile.setPisCst("99");
        OrderItem sold = persisted(new OrderItem(null, product(profile), 1, BigDecimal.ONE));

        assertThatThrownBy(() -> NfceOrderItemMapper.mapSimple(sold, "3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CST 99");
    }

    @Test
    void nonNinetyNineContributionsCarryNoAmounts() {
        NfceXmlBuilder.Item item = NfceOrderItemMapper.mapSimple(
                persisted(new OrderItem(null, product(completeProfile()), 1, BigDecimal.ONE)), "1");

        assertThat(item.taxes().pisOther()).isNull();
        assertThat(item.taxes().cofinsOther()).isNull();
    }

    @Test
    void rejectsHistoricalLineWithoutClassificationSnapshot() {
        OrderItem sold = persisted(new OrderItem(null, product(completeProfile()), 1, BigDecimal.ONE));
        ReflectionTestUtils.setField(sold, "fiscalNcmSnapshot", null);

        assertThatThrownBy(() -> NfceOrderItemMapper.mapSimple(sold, "1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("classification snapshot");
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
