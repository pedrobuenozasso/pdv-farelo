package com.farelo.api.cashier;

import java.math.BigDecimal;

/**
 * The JSON shape {@link CashMovementService#record} feeds to {@code
 * AuditLogService#record}'s {@code newValue} parameter — serializes to
 * exactly {@code {"type": "WITHDRAWAL", "amount": 50.00, "description":
 * "..."}}. Same "small named record" convention {@code
 * com.farelo.api.payment.PaymentSnapshot}'s javadoc documents for the
 * analogous {@code AuditLog} producer. {@code previousValue} stays {@code
 * null} — a cash movement is a creation, it has no "before" state. Package-
 * private: nothing outside {@code cashier} constructs or reads one
 * directly.
 */
record CashMovementSnapshot(CashMovementType type, BigDecimal amount, String description) {
}
