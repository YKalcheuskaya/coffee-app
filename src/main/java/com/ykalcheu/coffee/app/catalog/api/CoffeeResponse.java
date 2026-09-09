package com.ykalcheu.coffee.app.catalog.api;

import com.ykalcheu.coffee.app.catalog.domain.Coffee;

import java.math.BigDecimal;

public record CoffeeResponse(
        Long id,
        String name,
        String origin,
        String roastLevel,
        BigDecimal unitPrice,
        int availableQuantity
) {
    public static CoffeeResponse from(Coffee coffee) {
        return new CoffeeResponse(
                coffee.getId(),
                coffee.getName(),
                coffee.getOrigin(),
                coffee.getRoastLevel(),
                coffee.getUnitPrice(),
                coffee.getAvailableQuantity()
        );
    }
}
