package com.ykalcheu.coffee.app.order.api;

import com.ykalcheu.coffee.app.order.domain.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long coffeeId,
        String coffeeName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getCoffeeId(), item.getCoffeeName(), item.getUnitPrice(), item.getQuantity(), item.getLineTotal()
        );
    }
}
