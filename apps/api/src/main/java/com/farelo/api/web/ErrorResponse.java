package com.farelo.api.web;

/**
 * Standard error body for the API, per AGENTS.md:
 * {@code { "code": "...", "message": "...", "correlationId": "..." } }.
 */
public record ErrorResponse(String code, String message, String correlationId) {
}
