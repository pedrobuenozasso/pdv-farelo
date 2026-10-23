package com.farelo.api.fiscal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GtinTests {
    @Test
    void acceptsExplicitNoCodeAndGtinLengthsWithValidCheckDigit() {
        for (String value : new String[] {
                "SEM GTIN", "12345670", "123456789012", "7891234567895", "12345678901231"}) {
            assertTrue(Gtin.isValid(value), value);
        }
    }

    @Test
    void rejectsPendingMalformedOrIncorrectCheckDigit() {
        for (String value : new String[] {
                "", "sem gtin", "1234567", "7891234567896", "12345678901230", " 12345670"}) {
            assertFalse(Gtin.isValid(value), value);
        }
        assertFalse(Gtin.isValid(null));
    }
}
