package com.ykalcheu.coffee.app.catalog.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateCoffeeRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 120) String origin,
        @NotBlank @Size(max = 32) String roastLevel,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal unitPrice,
        @NotNull @PositiveOrZero Integer availableQuantity
) {
}
