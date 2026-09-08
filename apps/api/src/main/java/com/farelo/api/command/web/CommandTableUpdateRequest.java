package com.farelo.api.command.web;

import jakarta.validation.constraints.Positive;

/**
 * Request body for {@code PATCH /api/v1/commands/{number}/table}
 * (FARELO-315). {@code tableNumber} is nullable — omitting it (or sending
 * {@code null}) clears the comanda's table association (full-replace
 * semantics, same convention as {@code CommandCustomerUpdateRequest}; see
 * {@code CommandService#updateTable}'s javadoc). {@code @Positive} is a
 * no-op for a {@code null} value per the Bean Validation spec — it only
 * rejects a non-null, non-positive number.
 */
public record CommandTableUpdateRequest(
        @Positive(message = "tableNumber deve ser positivo") Integer tableNumber) {
}
