package com.ykalcheu.coffee.app.order.application;

import com.ykalcheu.coffee.app.order.api.OrderResponse;

public record OrderPlacementResult(OrderResponse order, boolean replayed) {
}
