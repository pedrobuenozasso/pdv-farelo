package com.farelo.api.customer.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/customers}. Never expose the JPA
 * entity directly on the API (see AGENTS.md) — this is the boundary DTO.
 *
 * <p>{@code phone}'s {@code @Pattern} is deliberately loose — same as
 * {@code CommandCustomerUpdateRequest#customerPhone}: it only rejects
 * letters/garbage, not a specific phone shape. Real normalization (digits
 * only, country code) happens server-side in
 * {@code CustomerService#normalizePhone}.
 */
public record CustomerRequest(
        @NotBlank(message = "name é obrigatório") String name,
        @Pattern(
                regexp = "^[0-9()+\\-\\s]*$",
                message = "phone deve conter apenas dígitos e pontuação de telefone")
        @Size(max = 30, message = "phone deve ter no máximo 30 caracteres")
        String phone) {
}
