package com.farelo.api.fiscal;

import com.farelo.api.ordering.OrderItem;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Converts one persisted, undiscounted simple sale line into an NFC-e item.
 * Configured options, discounts, cancelled lines and historical lines without
 * fiscal snapshots need a separate explicit representation; they are rejected
 * here so a catalog edit cannot silently change the product identity in the
 * sale's XML. Tax classification is also read from the sale snapshot, rather
 * than a mutable product/profile association.
 */
public final class NfceOrderItemMapper {
    private NfceOrderItemMapper() { }

    public static NfceXmlBuilder.Item mapSimple(OrderItem sold, String issuerCrt) {
        Objects.requireNonNull(sold, "sold item");
        if (sold.getId() == null || sold.isCancelled() || !sold.getOptions().isEmpty()) {
            throw new IllegalArgumentException("only persisted, active simple items are supported");
        }
        if (sold.getProduct() == null || sold.getProduct().getId() == null
                || sold.getProductNameSnapshot() == null || sold.getProductNameSnapshot().isBlank()
                || sold.getProductGtinSnapshot() == null || sold.getFiscalUnitSnapshot() == null) {
            throw new IllegalArgumentException("sale line is missing immutable fiscal product data");
        }
        if (sold.getQuantity() <= 0 || sold.getUnitPrice() == null || sold.getUnitPrice().signum() < 0) {
            throw new IllegalArgumentException("sale line has invalid quantity or frozen price");
        }
        if (!"1".equals(issuerCrt) && !"3".equals(issuerCrt)) {
            throw new IllegalArgumentException("unsupported issuer CRT");
        }
        String icms = "1".equals(issuerCrt) ? sold.getFiscalCsosnSnapshot() : sold.getFiscalCstSnapshot();
        if (icms == null || sold.getFiscalNcmSnapshot() == null || sold.getFiscalCfopSnapshot() == null
                || sold.getFiscalIcmsOriginSnapshot() == null || sold.getFiscalPisCstSnapshot() == null
                || sold.getFiscalCofinsCstSnapshot() == null) {
            throw new IllegalArgumentException("sale line lacks a complete fiscal classification snapshot");
        }
        // CST 99 needs explicit PIS/COFINS amounts that this profile does not store.
        if ("99".equals(sold.getFiscalPisCstSnapshot()) || "99".equals(sold.getFiscalCofinsCstSnapshot())) {
            throw new IllegalArgumentException("contribution CST 99 requires explicit amounts");
        }
        BigDecimal quantity = BigDecimal.valueOf(sold.getQuantity());
        BigDecimal total = sold.getUnitPrice().multiply(quantity);
        return new NfceXmlBuilder.Item(
                sold.getProduct().getId().toString(), sold.getProductNameSnapshot(),
                sold.getProductGtinSnapshot(), sold.getFiscalNcmSnapshot(), sold.getFiscalCestSnapshot(),
                sold.getFiscalCfopSnapshot(), sold.getFiscalUnitSnapshot(), quantity,
                sold.getUnitPrice(), total,
                new NfceXmlBuilder.Taxes(sold.getFiscalIcmsOriginSnapshot(), icms,
                        sold.getFiscalPisCstSnapshot(), sold.getFiscalCofinsCstSnapshot()));
    }
}
