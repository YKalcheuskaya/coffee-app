package com.ykalcheu.coffee.app.order.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PlaceOrderRequest(@NotEmpty @Size(max = 100) List<@NotNull @Valid OrderItemRequest> items) {
}
