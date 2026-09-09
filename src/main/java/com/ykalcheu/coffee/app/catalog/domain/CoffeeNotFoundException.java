package com.ykalcheu.coffee.app.catalog.domain;

public class CoffeeNotFoundException extends RuntimeException {

    private final long coffeeId;

    public CoffeeNotFoundException(long coffeeId) {
        super("Coffee not found.");
        this.coffeeId = coffeeId;
    }

    public long getCoffeeId() {
        return coffeeId;
    }
}
