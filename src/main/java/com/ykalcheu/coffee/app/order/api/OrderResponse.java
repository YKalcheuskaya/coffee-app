package com.ykalcheu.coffee.app.order.api;

import com.ykalcheu.coffee.app.order.domain.CoffeeOrder;
import com.ykalcheu.coffee.app.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        OrderStatus status,
        Instant createdAt,
        String currency,
        BigDecimal totalAmount,
        List<OrderItemResponse> items
) {
    public static OrderResponse from(CoffeeOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getCurrency(),
                order.getTotalAmount(),
                order.getItems().stream().map(OrderItemResponse::from).toList()
        );
    }
}
