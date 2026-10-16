package com.farelo.api.payment.web;

import com.farelo.api.web.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice
public class PaymentIdempotencyExceptionHandler {
    @ExceptionHandler(PaymentIdempotencyException.class)
    public ResponseEntity<ErrorResponse> handle(PaymentIdempotencyException ex) {
        HttpStatus status = ex.isInvalidKey() ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT;
        String code = ex.isInvalidKey() ? "INVALID_IDEMPOTENCY_KEY" : "IDEMPOTENCY_KEY_REUSED";
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code, ex.getMessage(), UUID.randomUUID().toString()));
    }
}
