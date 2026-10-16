package com.farelo.api.cashier.web;

import com.farelo.api.cashier.CashSessionOpeningAmountUnknownException;
import com.farelo.api.web.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice
public class CashSessionLegacyExceptionHandler {

    @ExceptionHandler(CashSessionOpeningAmountUnknownException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse openingAmountUnknown(CashSessionOpeningAmountUnknownException ex) {
        return new ErrorResponse("CASH_SESSION_OPENING_AMOUNT_UNKNOWN", ex.getMessage(), UUID.randomUUID().toString());
    }
}
