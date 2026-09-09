package com.ykalcheu.coffee.app.order.domain;

public class InsufficientInventoryException extends RuntimeException {

    private final long coffeeId;
    private final int requestedQuantity;
    private final int availableQuantity;

    public InsufficientInventoryException(long coffeeId, int requestedQuantity, int availableQuantity) {
        super("Requested quantity exceeds available inventory.");
        this.coffeeId = coffeeId;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }

    public long getCoffeeId() {
        return coffeeId;
    }

    public int getRequestedQuantity() {
        return requestedQuantity;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }
}
