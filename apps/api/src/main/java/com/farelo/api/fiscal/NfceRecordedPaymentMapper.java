package com.farelo.api.fiscal;

import com.farelo.api.payment.Payment;
import com.farelo.api.payment.PaymentMethod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Converts the immutable payment ledger to NFC-e payment lines. Electronic
 * transaction details must be explicitly captured and supplied by payment ID;
 * the current cashier API does not yet persist them. No fiscal value is inferred
 * from a payment method alone.
 */
public final class NfceRecordedPaymentMapper {
    private NfceRecordedPaymentMapper() { }

    public static List<NfceXmlBuilder.Payment> map(
            List<Payment> recorded, Map<UUID, NfceXmlBuilder.ElectronicDetails> electronicByPaymentId) {
        Objects.requireNonNull(recorded, "recorded payments");
        Objects.requireNonNull(electronicByPaymentId, "electronic payment details");
        if (recorded.isEmpty() || recorded.size() > 100) {
            throw new IllegalArgumentException("1 to 100 recorded payments are required");
        }
        List<NfceXmlBuilder.Payment> fiscal = new ArrayList<>(recorded.size());
        Set<UUID> seen = new HashSet<>();
        for (Payment payment : recorded) {
            if (payment == null || payment.getId() == null || !seen.add(payment.getId())
                    || payment.getMethod() == null || payment.getAmount() == null
                    || payment.getAmount().signum() <= 0) {
                throw new IllegalArgumentException("invalid or duplicate recorded payment");
            }
            PaymentMethod method = payment.getMethod();
            var electronic = electronicByPaymentId.get(payment.getId());
            NfceXmlBuilder.PaymentMethod fiscalMethod;
            switch (method) {
                case CASH -> {
                    if (electronic != null) {
                        throw new IllegalArgumentException("cash payment cannot carry electronic details");
                    }
                    fiscalMethod = NfceXmlBuilder.PaymentMethod.CASH;
                }
                case PIX -> {
                    fiscalMethod = NfceXmlBuilder.PaymentMethod.PIX;
                    requireElectronic(electronic);
                }
                case CREDIT_CARD -> {
                    fiscalMethod = NfceXmlBuilder.PaymentMethod.CREDIT_CARD;
                    requireElectronic(electronic);
                }
                case DEBIT_CARD -> {
                    fiscalMethod = NfceXmlBuilder.PaymentMethod.DEBIT_CARD;
                    requireElectronic(electronic);
                }
                case OTHER -> throw new IllegalArgumentException(
                        "OTHER payment needs an explicitly supported fiscal method");
                default -> throw new IllegalArgumentException("unsupported recorded payment method");
            }
            fiscal.add(new NfceXmlBuilder.Payment(fiscalMethod, payment.getAmount(), electronic));
        }
        if (!seen.containsAll(electronicByPaymentId.keySet())) {
            throw new IllegalArgumentException("electronic details do not belong to recorded payments");
        }
        return List.copyOf(fiscal);
    }

    private static void requireElectronic(NfceXmlBuilder.ElectronicDetails details) {
        if (details == null || details.integrationType() == null) {
            throw new IllegalArgumentException("electronic payment lacks recorded fiscal details");
        }
    }
}
