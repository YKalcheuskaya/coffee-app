package com.ykalcheu.coffee.app.catalog.domain;

public class CoffeeHasActiveOrdersException extends RuntimeException {

    private final long coffeeId;

    public CoffeeHasActiveOrdersException(long coffeeId) {
        super("Coffee cannot be deleted while it belongs to a placed order.");
        this.coffeeId = coffeeId;
    }

    public long getCoffeeId() {
        return coffeeId;
    }
}
