package com.ykalcheu.coffee.app.order.domain;

public class OrderNotFoundException extends RuntimeException {

    private final long orderId;

    public OrderNotFoundException(long orderId) {
        super("Order not found.");
        this.orderId = orderId;
    }

    public long getOrderId() {
        return orderId;
    }
}
