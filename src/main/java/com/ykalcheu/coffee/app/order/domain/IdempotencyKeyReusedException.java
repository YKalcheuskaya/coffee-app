package com.ykalcheu.coffee.app.order.domain;

public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException() {
        super("Idempotency-Key was already used with a different order request.");
    }
}
