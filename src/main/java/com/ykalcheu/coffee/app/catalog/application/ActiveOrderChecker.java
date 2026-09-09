package com.ykalcheu.coffee.app.catalog.application;

public interface ActiveOrderChecker {

    boolean hasActiveOrdersForCoffee(long coffeeId);
}
