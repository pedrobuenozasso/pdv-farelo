package com.farelo.api.fiscal;

/** GS1 GTIN-8, GTIN-12, GTIN-13, GTIN-14 or the explicit NFC-e no-code marker. */
public final class Gtin {
    private Gtin() { }

    public static boolean isValid(String value) {
        if ("SEM GTIN".equals(value)) return true;
        if (value == null || !value.matches("[0-9]{8}|[0-9]{12,14}")) return false;
        int sum = 0;
        for (int i = value.length() - 2, weight = 3; i >= 0; i--, weight = weight == 3 ? 1 : 3) {
            sum += (value.charAt(i) - '0') * weight;
        }
        return (10 - sum % 10) % 10 == value.charAt(value.length() - 1) - '0';
    }
}
