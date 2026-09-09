package com.ykalcheu.coffee.app.order.domain;

public class DuplicateCoffeeItemException extends RuntimeException {

    private final long coffeeId;

    public DuplicateCoffeeItemException(long coffeeId) {
        super("A coffee may appear only once in an order.");
        this.coffeeId = coffeeId;
    }

    public long getCoffeeId() {
        return coffeeId;
    }
}
