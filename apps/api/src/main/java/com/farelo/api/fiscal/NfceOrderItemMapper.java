package com.farelo.api.fiscal;

import com.farelo.api.ordering.OrderItem;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Converts one persisted, undiscounted simple sale line into an NFC-e item.
 * Configured options, discounts, cancelled lines and historical lines without
 * fiscal snapshots need a separate explicit representation; they are rejected
 * here so a catalog edit cannot silently change the product identity in the
 * sale's XML. Fiscal profile fields still need to be frozen when an issuance
 * attempt is prepared; this mapper alone does not guarantee that.
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
        FiscalProfile profile = sold.getProduct().getFiscalProfile();
        if (profile == null || !profile.isActive()) {
            throw new IllegalArgumentException("product has no active fiscal profile");
        }
        if (!"1".equals(issuerCrt) && !"3".equals(issuerCrt)) {
            throw new IllegalArgumentException("unsupported issuer CRT");
        }
        String icms = "1".equals(issuerCrt) ? profile.getCsosn() : profile.getCst();
        if (icms == null || profile.getNcm() == null || profile.getCfop() == null
                || profile.getIcmsOrigin() == null || profile.getPisCst() == null
                || profile.getCofinsCst() == null) {
            throw new IllegalArgumentException("fiscal profile is incomplete for NFC-e");
        }
        // CST 99 needs explicit PIS/COFINS amounts that this profile does not store.
        if ("99".equals(profile.getPisCst()) || "99".equals(profile.getCofinsCst())) {
            throw new IllegalArgumentException("contribution CST 99 requires explicit amounts");
        }
        BigDecimal quantity = BigDecimal.valueOf(sold.getQuantity());
        BigDecimal total = sold.getUnitPrice().multiply(quantity);
        return new NfceXmlBuilder.Item(
                sold.getProduct().getId().toString(), sold.getProductNameSnapshot(),
                sold.getProductGtinSnapshot(), profile.getNcm(), profile.getCest(),
                profile.getCfop(), sold.getFiscalUnitSnapshot(), quantity,
                sold.getUnitPrice(), total,
                new NfceXmlBuilder.Taxes(profile.getIcmsOrigin(), icms,
                        profile.getPisCst(), profile.getCofinsCst()));
    }
}
