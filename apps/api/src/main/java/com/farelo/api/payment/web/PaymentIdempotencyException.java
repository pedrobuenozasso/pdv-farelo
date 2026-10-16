package com.farelo.api.payment.web;

public class PaymentIdempotencyException extends RuntimeException {
    private final boolean invalidKey;

    private PaymentIdempotencyException(String message, boolean invalidKey) {
        super(message);
        this.invalidKey = invalidKey;
    }

    static PaymentIdempotencyException invalidKey() {
        return new PaymentIdempotencyException("Idempotency-Key deve ser um UUID canônico", true);
    }

    static PaymentIdempotencyException reused() {
        return new PaymentIdempotencyException("Idempotency-Key já foi usado com outro pagamento", false);
    }

    public boolean isInvalidKey() {
        return invalidKey;
    }
}
