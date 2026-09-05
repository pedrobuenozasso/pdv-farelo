package com.farelo.api.payment;

import java.math.BigDecimal;

/**
 * FARELO-305: the JSON shape {@link PaymentService#record} feeds to {@code
 * AuditLogService#record}'s {@code newValue} parameter — serializes to
 * exactly {@code {"amount": 25.00, "method": "PIX"}}. Same "small named
 * record" convention {@code com.farelo.api.catalog.ProductPriceSnapshot}'s
 * javadoc documents for the analogous {@code AuditLog} producer.
 * {@code previousValue} stays {@code null} (see {@code AuditLog}'s javadoc,
 * "Design decision 4") — a payment is a creation, it has no "before" state.
 * Package-private: nothing outside {@code payment} constructs or reads one
 * directly.
 */
record PaymentSnapshot(BigDecimal amount, PaymentMethod method) {
}
