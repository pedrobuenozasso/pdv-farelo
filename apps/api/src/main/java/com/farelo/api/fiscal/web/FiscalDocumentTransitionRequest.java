package com.farelo.api.fiscal.web;

import com.farelo.api.fiscal.FiscalDocumentStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST
 * /api/v1/commands/{number}/fiscal-documents/{id}/transition} (FARELO-157).
 * A single required field, {@code status} — the {@link FiscalDocumentStatus}
 * the caller wants the document moved to. {@code @NotNull} is the only
 * validation here (a missing/malformed enum value is already rejected by
 * Jackson's own deserialization before this record is even constructed,
 * same as every other enum-carrying request body in this codebase, e.g.
 * {@code com.farelo.api.payment.web.PaymentRequest#method}); whether the
 * requested value is actually a <em>legal</em> move from the document's
 * current status is {@code FiscalDocumentService}'s job (the transition
 * table), not this DTO's.
 */
public record FiscalDocumentTransitionRequest(@NotNull FiscalDocumentStatus status) {
}
