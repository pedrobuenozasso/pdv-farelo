package com.farelo.api.customer.web;

import com.farelo.api.customer.Customer;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body exposing only the public fields of {@link Customer} — the
 * JPA entity itself is never returned by the API (see AGENTS.md).
 */
public record CustomerResponse(
        UUID id,
        String name,
        String phone,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getPhone(),
                customer.getCreatedAt(),
                customer.getUpdatedAt());
    }

}
