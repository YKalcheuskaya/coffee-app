package com.ykalcheu.coffee.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Boots the Coffee App HTTP API and its Spring-managed infrastructure.
 */
@SpringBootApplication
public class CoffeeAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoffeeAppApplication.class, args);
    }
}
