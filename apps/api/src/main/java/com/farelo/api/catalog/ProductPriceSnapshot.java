package com.farelo.api.catalog;

import java.math.BigDecimal;

/**
 * FARELO-126: the JSON shape {@link ProductService#update} feeds to {@code
 * AuditLogService#record}'s {@code previousValue}/{@code newValue}
 * parameters when a product's price actually changes — serializes (via the
 * injected {@link com.fasterxml.jackson.databind.ObjectMapper}, same
 * "record + {@code writeValueAsString}" pattern already used by {@code
 * PrintJobService#serialize} for {@code PrintJobContent}) to exactly
 * {@code {"price": 12.50}}, the literal shape {@code AuditLog}'s own javadoc
 * ("Design decision 4") already anticipated for this producer.
 *
 * <p>Deliberately just the one field, unlike {@code PrintJobContent}/{@code
 * OrderCreatedEvent}: this snapshot exists only to describe <em>what a price
 * change ticket needs to show</em> — the product's price before/after — not
 * a general "what does a Product look like" snapshot. FARELO-126 audits
 * price changes specifically (see {@code ProductService#update}'s javadoc
 * for why only the price field, not every edited field, triggers an audit
 * row), so the snapshot it writes mirrors that same narrow scope. Package-
 * private: no code outside {@code catalog} constructs or reads one directly
 * — everything else only ever sees its serialized JSON form via {@code
 * AuditLog}/{@code AuditLogResponse}.
 */
record ProductPriceSnapshot(BigDecimal price) {
}
