package com.ykalcheu.coffee.app.order.domain;

public class InvalidIdempotencyKeyException extends RuntimeException {

    public InvalidIdempotencyKeyException() {
        super("Idempotency-Key must be present, non-blank, and at most 120 characters.");
    }
}
