package com.farelo.api.web;

import com.farelo.api.catalog.CategoryNotFoundException;
import com.farelo.api.catalog.ProductNotAvailableException;
import com.farelo.api.catalog.ProductNotFoundException;
import com.farelo.api.command.CommandCannotAcceptOrdersException;
import com.farelo.api.command.CommandCannotBeClosedException;
import com.farelo.api.command.CommandNotAvailableException;
import com.farelo.api.command.CommandNotFoundException;
import com.farelo.api.ordering.OrderInvalidTransitionException;
import com.farelo.api.ordering.OrderNotFoundException;
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
 * come up in future tickets. There are now three "not found" exceptions
 * following the same shape ({@link CategoryNotFoundException},
 * {@link ProductNotFoundException}, {@link CommandNotFoundException}) —
 * the latter in a different domain ({@code command}), which is the trigger
 * previously flagged (FARELO-016) for reconsidering a shared marker/base
 * exception type instead of one handler per class. Still not introduced
 * here (FARELO-032) — each handler stays a trivial one-liner and the
 * lookup key differs (UUID {@code id} for Category/Product, {@code int
 * number} for Command), so a shared base would need to abstract over that
 * too. Worth a dedicated look if a fourth case appears.
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

    @ExceptionHandler(CategoryNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleCategoryNotFound(CategoryNotFoundException ex) {
        return new ErrorResponse("CATEGORY_NOT_FOUND", ex.getMessage(), UUID.randomUUID().toString());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleProductNotFound(ProductNotFoundException ex) {
        return new ErrorResponse("PRODUCT_NOT_FOUND", ex.getMessage(), UUID.randomUUID().toString());
    }

    @ExceptionHandler(CommandNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleCommandNotFound(CommandNotFoundException ex) {
        return new ErrorResponse("COMMAND_NOT_FOUND", ex.getMessage(), UUID.randomUUID().toString());
    }

    // 409 Conflict: the request is well-formed, but the command's current
    // state conflicts with the requested state transition.
    @ExceptionHandler(CommandNotAvailableException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleCommandNotAvailable(CommandNotAvailableException ex) {
        return new ErrorResponse("COMMAND_NOT_AVAILABLE", ex.getMessage(), UUID.randomUUID().toString());
    }

    // 409 Conflict, same reasoning as CommandNotAvailableException above.
    @ExceptionHandler(CommandCannotBeClosedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleCommandCannotBeClosed(CommandCannotBeClosedException ex) {
        return new ErrorResponse("COMMAND_CANNOT_BE_CLOSED", ex.getMessage(), UUID.randomUUID().toString());
    }

    // 409 Conflict, same reasoning as the other command state-conflict
    // exceptions above.
    @ExceptionHandler(CommandCannotAcceptOrdersException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleCommandCannotAcceptOrders(CommandCannotAcceptOrdersException ex) {
        return new ErrorResponse("COMMAND_CANNOT_ACCEPT_ORDERS", ex.getMessage(), UUID.randomUUID().toString());
    }

    // 409 Conflict: the product exists but is currently not sellable.
    @ExceptionHandler(ProductNotAvailableException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleProductNotAvailable(ProductNotAvailableException ex) {
        return new ErrorResponse("PRODUCT_NOT_AVAILABLE", ex.getMessage(), UUID.randomUUID().toString());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleOrderNotFound(OrderNotFoundException ex) {
        return new ErrorResponse("ORDER_NOT_FOUND", ex.getMessage(), UUID.randomUUID().toString());
    }

    // 409 Conflict, same state-conflict reasoning as the command
    // exceptions above.
    @ExceptionHandler(OrderInvalidTransitionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleOrderInvalidTransition(OrderInvalidTransitionException ex) {
        return new ErrorResponse("ORDER_INVALID_TRANSITION", ex.getMessage(), UUID.randomUUID().toString());
    }

    private static String describe(FieldError fieldError) {
        return "%s: %s".formatted(fieldError.getField(), fieldError.getDefaultMessage());
    }

}
