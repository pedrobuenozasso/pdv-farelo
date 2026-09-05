package com.farelo.api.ordering;

/**
 * FARELO-305: the JSON shape {@link OrderService#markAsCancelled} feeds to
 * {@code AuditLogService#record}'s {@code previousValue}/{@code newValue}
 * parameters — serializes to exactly {@code {"status": "CREATED"}} (or
 * whichever status). Same "small named record, one field" convention
 * {@code com.farelo.api.catalog.ProductPriceSnapshot}'s javadoc already
 * documents for the analogous {@code AuditLog} producer. Package-private:
 * nothing outside {@code ordering} constructs or reads one directly.
 */
record OrderCancellationSnapshot(OrderStatus status) {
}
