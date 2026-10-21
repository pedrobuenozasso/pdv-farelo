package com.farelo.api.fiscal;

import com.farelo.api.payment.Payment;
import com.farelo.api.payment.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NfceRecordedPaymentMapperTests {
    @Test
    void preservesRecordedSplitAmountsAndExplicitPixDetails() {
        Payment cash = payment(PaymentMethod.CASH, "12.50");
        Payment pix = payment(PaymentMethod.PIX, "7.50");
        var evidence = new NfceXmlBuilder.ElectronicDetails("2", null, null, null, null, null);

        var lines = NfceRecordedPaymentMapper.map(List.of(cash, pix), Map.of(pix.getId(), evidence));

        assertEquals(List.of(
                new NfceXmlBuilder.Payment(NfceXmlBuilder.PaymentMethod.CASH, new BigDecimal("12.50")),
                new NfceXmlBuilder.Payment(NfceXmlBuilder.PaymentMethod.PIX, new BigDecimal("7.50"), evidence)),
                lines);
    }

    @Test
    void refusesToGuessElectronicDetailsOrFiscalCodeForOther() {
        Payment card = payment(PaymentMethod.CREDIT_CARD, "20.00");
        Payment other = payment(PaymentMethod.OTHER, "20.00");
        Payment cash = payment(PaymentMethod.CASH, "20.00");

        assertThrows(IllegalArgumentException.class,
                () -> NfceRecordedPaymentMapper.map(List.of(card), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> NfceRecordedPaymentMapper.map(List.of(other), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> NfceRecordedPaymentMapper.map(List.of(cash), Map.of(
                        cash.getId(), new NfceXmlBuilder.ElectronicDetails("2", null, null, null, null, null))));
        assertThrows(IllegalArgumentException.class,
                () -> NfceRecordedPaymentMapper.map(List.of(cash, cash), Map.of()));
    }

    private static Payment payment(PaymentMethod method, String amount) {
        Payment payment = new Payment(null, new BigDecimal(amount), method, null);
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        return payment;
    }
}
