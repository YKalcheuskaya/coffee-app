package com.ykalcheu.coffee.app.order.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(
        @NotNull @Positive Long coffeeId,
        @NotNull @Positive Integer quantity
) {
}
