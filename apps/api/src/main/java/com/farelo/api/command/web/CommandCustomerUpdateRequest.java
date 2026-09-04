package com.farelo.api.command.web;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/v1/commands/{number}/customer}
 * (FARELO-190/191). Both fields optional/nullable — omitting or blanking
 * either clears it (full-replace semantics, same as every other non-
 * transition write in this codebase; see {@code
 * CommandService#updateCustomer}'s javadoc).
 *
 * <p>{@code customerPhone}'s {@code @Pattern} is deliberately loose — it
 * only rejects letters/garbage (staff fat-fingering a name into the phone
 * field, say), not a specific phone shape: real normalization (digits
 * only, country code) happens server-side in {@code
 * CommandService#normalizePhone}, which accepts whatever punctuation a
 * human would naturally type ({@code "(31) 99876-5432"}, {@code
 * "+55 31 99876-5432"}, {@code "31999765432"}, ...).
 */
public record CommandCustomerUpdateRequest(
        @Size(max = 120, message = "customerName deve ter no máximo 120 caracteres")
        String customerName,
        @Pattern(
                regexp = "^[0-9()+\\-\\s]*$",
                message = "customerPhone deve conter apenas dígitos e pontuação de telefone")
        @Size(max = 30, message = "customerPhone deve ter no máximo 30 caracteres")
        String customerPhone) {
}
