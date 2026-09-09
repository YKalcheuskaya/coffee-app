package com.ykalcheu.coffee.app.catalog.domain;

public class CoffeeNameAlreadyExistsException extends RuntimeException {

    public CoffeeNameAlreadyExistsException() {
        super("A coffee with this name already exists.");
    }
}
