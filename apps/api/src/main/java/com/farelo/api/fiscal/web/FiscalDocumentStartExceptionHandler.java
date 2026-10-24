package com.farelo.api.fiscal.web;

import com.farelo.api.fiscal.FiscalDocumentStartException;
import com.farelo.api.web.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice
public class FiscalDocumentStartExceptionHandler {
    @ExceptionHandler(FiscalDocumentStartException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handle(FiscalDocumentStartException ex) {
        return new ErrorResponse(ex.getCode(), ex.getMessage(), UUID.randomUUID().toString());
    }
}
