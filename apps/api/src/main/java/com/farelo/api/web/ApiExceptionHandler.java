package com.farelo.api.web;

import com.farelo.api.catalog.CategoryNotFoundException;
import com.farelo.api.catalog.ProductNotAvailableException;
import com.farelo.api.catalog.ProductNotFoundException;
import com.farelo.api.command.CommandCannotAcceptOrdersException;
import com.farelo.api.command.CommandCannotAcceptPaymentsException;
import com.farelo.api.command.CommandCannotBeClosedException;
import com.farelo.api.command.CommandNotAvailableException;
import com.farelo.api.command.CommandNotFoundException;
import com.farelo.api.fiscal.CompanyFiscalConfigurationNotFoundException;
import com.farelo.api.fiscal.FiscalProfileNotFoundException;
import com.farelo.api.inventory.IngredientNotFoundException;
import com.farelo.api.inventory.RecipeAlreadyExistsException;
import com.farelo.api.inventory.RecipeItemAlreadyExistsException;
import com.farelo.api.inventory.RecipeItemNotFoundException;
import com.farelo.api.inventory.RecipeNotFoundException;
import com.farelo.api.notification.NotificationNotFoundException;
import com.farelo.api.ordering.OrderInvalidTransitionException;
import com.farelo.api.ordering.OrderNotFoundException;
import com.farelo.api.printing.PrintJobInvalidTransitionException;
import com.farelo.api.printing.PrintJobNotFoundException;
import com.farelo.api.printing.PrintJobRetryLimitExceededException;
import com.farelo.api.security.InvalidCredentialsException;
import com.farelo.api.security.UserEmailAlreadyExistsException;
import com.farelo.api.security.UserNotFoundException;
import com.farelo.api.security.auth.InvalidTokenException;
import com.farelo.api.security.rbac.InsufficientRoleException;
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

    // 409 Conflict, same reasoning as the other command state-conflict
    // exceptions above (FARELO-141).
    @ExceptionHandler(CommandCannotAcceptPaymentsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleCommandCannotAcceptPayments(CommandCannotAcceptPaymentsException ex) {
        return new ErrorResponse("COMMAND_CANNOT_ACCEPT_PAYMENTS", ex.getMessage(), UUID.randomUUID().toString());
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

    @ExceptionHandler(PrintJobNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handlePrintJobNotFound(PrintJobNotFoundException ex) {
        return new ErrorResponse("PRINT_JOB_NOT_FOUND", ex.getMessage(), UUID.randomUUID().toString());
    }

    // 409 Conflict, same state-conflict reasoning as OrderInvalidTransitionException
    // above (FARELO-077).
    @ExceptionHandler(PrintJobInvalidTransitionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handlePrintJobInvalidTransition(PrintJobInvalidTransitionException ex) {
        return new ErrorResponse("PRINT_JOB_INVALID_TRANSITION", ex.getMessage(), UUID.randomUUID().toString());
    }

    // 409 Conflict, distinct code from PrintJobInvalidTransitionException
    // above (FARELO-079) — see PrintJobRetryLimitExceededException's javadoc
    // for why this is a separate exception/code rather than reusing that one.
    @ExceptionHandler(PrintJobRetryLimitExceededException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handlePrintJobRetryLimitExceeded(PrintJobRetryLimitExceededException ex) {
        return new ErrorResponse("PRINT_JOB_RETRY_LIMIT_EXCEEDED", ex.getMessage(), UUID.randomUUID().toString());
    }

    @ExceptionHandler(IngredientNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleIngredientNotFound(IngredientNotFoundException ex) {
        return new ErrorResponse("INGREDIENT_NOT_FOUND", ex.getMessage(), UUID.randomUUID().toString());
    }

    // FARELO-150: id given to GET/PUT /api/v1/fiscal-profiles/{id} doesn't
    // exist — same "not found" shape as every other *NotFoundException
    // handler in this class.
    @ExceptionHandler(FiscalProfileNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleFiscalProfileNotFound(FiscalProfileNotFoundException ex) {
        return new ErrorResponse("FISCAL_PROFILE_NOT_FOUND", ex.getMessage(), UUID.randomUUID().toString());
    }

    // FARELO-155: GET /api/v1/company-fiscal-configuration before PUT has
    // ever been called — same "not found" shape as every other
    // *NotFoundException handler above, but with no id (there is nothing
    // to identify, only "configured" vs. "not configured yet").
    @ExceptionHandler(CompanyFiscalConfigurationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleCompanyFiscalConfigurationNotFound(CompanyFiscalConfigurationNotFoundException ex) {
        return new ErrorResponse("COMPANY_FISCAL_CONFIGURATION_NOT_FOUND", ex.getMessage(), UUID.randomUUID().toString());
    }

    @ExceptionHandler(RecipeNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleRecipeNotFound(RecipeNotFoundException ex) {
        return new ErrorResponse("RECIPE_NOT_FOUND", ex.getMessage(), UUID.randomUUID().toString());
    }

    // 409 Conflict: the product already has an active recipe — see
    // Recipe's javadoc.
    @ExceptionHandler(RecipeAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleRecipeAlreadyExists(RecipeAlreadyExistsException ex) {
        return new ErrorResponse("RECIPE_ALREADY_EXISTS", ex.getMessage(), UUID.randomUUID().toString());
    }

    @ExceptionHandler(RecipeItemNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleRecipeItemNotFound(RecipeItemNotFoundException ex) {
        return new ErrorResponse("RECIPE_ITEM_NOT_FOUND", ex.getMessage(), UUID.randomUUID().toString());
    }

    // 409 Conflict: the recipe already has a line for this ingredient — see
    // RecipeItemAlreadyExistsException's javadoc.
    @ExceptionHandler(RecipeItemAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleRecipeItemAlreadyExists(RecipeItemAlreadyExistsException ex) {
        return new ErrorResponse("RECIPE_ITEM_ALREADY_EXISTS", ex.getMessage(), UUID.randomUUID().toString());
    }

    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleUserNotFound(UserNotFoundException ex) {
        return new ErrorResponse("USER_NOT_FOUND", ex.getMessage(), UUID.randomUUID().toString());
    }

    // 409 Conflict: email is the (future) login identifier and must be
    // unique — see UserEmailAlreadyExistsException's javadoc.
    @ExceptionHandler(UserEmailAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleUserEmailAlreadyExists(UserEmailAlreadyExistsException ex) {
        return new ErrorResponse("USER_EMAIL_ALREADY_EXISTS", ex.getMessage(), UUID.randomUUID().toString());
    }

    // FARELO-111: id given to POST /api/v1/notifications/{id}/send doesn't
    // exist — same "not found" shape as every other *NotFoundException
    // handler above.
    @ExceptionHandler(NotificationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotificationNotFound(NotificationNotFoundException ex) {
        return new ErrorResponse("NOTIFICATION_NOT_FOUND", ex.getMessage(), UUID.randomUUID().toString());
    }

    // FARELO-121: POST /api/v1/auth/login with a wrong email or wrong
    // password (or a matching-but-inactive user) — 401, one generic
    // code/message for every cause, never distinguishing "email doesn't
    // exist" from "password is wrong". See InvalidCredentialsException's
    // javadoc for why.
    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidCredentials(InvalidCredentialsException ex) {
        return new ErrorResponse("INVALID_CREDENTIALS", ex.getMessage(), UUID.randomUUID().toString());
    }

    // FARELO-122: com.farelo.api.security.rbac.RoleAuthorizationInterceptor
    // throws this for a missing/malformed Authorization header or a token
    // JwtTokenService#parse rejects (bad signature, malformed, expired) —
    // one generic code/status for every cause, the same "the caller isn't
    // authenticated, full stop" collapse InvalidTokenException's javadoc
    // documents. Reachable only via FARELO-122's dedicated test controller
    // until FARELO-123, which is the first ticket to require a token on any
    // production endpoint (the Admin surface — see RequireRole's javadoc).
    @ExceptionHandler(InvalidTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidToken(InvalidTokenException ex) {
        return new ErrorResponse("UNAUTHENTICATED", ex.getMessage(), UUID.randomUUID().toString());
    }

    // FARELO-122: RoleAuthorizationInterceptor throws this when a caller IS
    // authenticated (unlike InvalidTokenException above) but their
    // UserRole isn't one of the handler's @RequireRole-allowed roles — 403,
    // distinct from the 401 above. Same reachability caveat as above (only
    // a real production path since FARELO-123).
    @ExceptionHandler(InsufficientRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleInsufficientRole(InsufficientRoleException ex) {
        return new ErrorResponse("FORBIDDEN", ex.getMessage(), UUID.randomUUID().toString());
    }

    private static String describe(FieldError fieldError) {
        return "%s: %s".formatted(fieldError.getField(), fieldError.getDefaultMessage());
    }

}
