package com.ykalcheu.coffee.app.order.domain;

public class OrderAlreadyCancelledException extends RuntimeException {

    private final long orderId;

    public OrderAlreadyCancelledException(long orderId) {
        super("Order has already been cancelled.");
        this.orderId = orderId;
    }

    public long getOrderId() {
        return orderId;
    }
}
