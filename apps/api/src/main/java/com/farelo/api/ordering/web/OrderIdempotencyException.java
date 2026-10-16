package com.farelo.api.ordering.web;

public class OrderIdempotencyException extends RuntimeException {
    private final boolean invalidKey;

    private OrderIdempotencyException(String message, boolean invalidKey) {
        super(message);
        this.invalidKey = invalidKey;
    }

    static OrderIdempotencyException invalidKey() {
        return new OrderIdempotencyException("Idempotency-Key deve ser um UUID canônico", true);
    }

    static OrderIdempotencyException reused() {
        return new OrderIdempotencyException("Idempotency-Key já foi usado com outro pedido", false);
    }

    public boolean isInvalidKey() {
        return invalidKey;
    }
}
