package com.ykalcheu.coffee.app.catalog.application;

/**
 * Cross-feature policy port used by the catalog to protect order history without depending on order persistence details.
 */
public interface ActiveOrderChecker {

    boolean hasActiveOrdersForCoffee(long coffeeId);
}
