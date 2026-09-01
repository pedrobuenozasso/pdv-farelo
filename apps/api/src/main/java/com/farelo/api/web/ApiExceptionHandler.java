package com.farelo.api.web;

import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * Translates exceptions into the standard error format defined in AGENTS.md:
 * {@code { "code": "...", "message": "...", "correlationId": "..." } }.
 *
 * <p>This is shared infrastructure for every endpoint, not just
 * {@code catalog} — new exception types should be handled here as they
 * come up in future tickets.
 *
 * <p>{@code correlationId} is a freshly generated id per error for now;
 * wiring it to a request-scoped id shared across logs (e.g. via a servlet
 * filter + MDC) is left for a future ticket, since it is orthogonal to this
 * one and would affect every request, not just error responses.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(ApiExceptionHandler::describe)
                .orElse("Validation failed");

        return new ErrorResponse("VALIDATION_ERROR", message, UUID.randomUUID().toString());
    }

    private static String describe(FieldError fieldError) {
        return "%s: %s".formatted(fieldError.getField(), fieldError.getDefaultMessage());
    }

}
