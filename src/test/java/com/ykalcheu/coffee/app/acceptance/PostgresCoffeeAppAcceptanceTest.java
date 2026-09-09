package com.ykalcheu.coffee.app.acceptance;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PostgresCoffeeAppAcceptanceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void shouldServeFlywaySeededCatalogThroughHttpAgainstPostgres() {
        given()
                .port(port)
                .when()
                .get("/api/coffees")
                .then()
                .statusCode(200)
                .body(containsString("Espresso"));
    }

    @Test
    void shouldAllowOnlyOneConcurrentOrderForTheLastAvailableItem() throws Exception {
        long coffeeId = given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "Concurrent Order Coffee",
                        "origin", "Brazil",
                        "roastLevel", "DARK",
                        "unitPrice", 4.00,
                        "availableQuantity", 1
                ))
                .when()
                .post("/api/coffees")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getLong("id");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> outcomes = executor.invokeAll(List.of(
                    () -> placeOneItemOrder(coffeeId),
                    () -> placeOneItemOrder(coffeeId)
            ));

            assertThat(outcomes.stream().map(this::getStatusCode).toList())
                    .containsExactlyInAnyOrder(201, 409);
        } finally {
            executor.shutdownNow();
        }

        given()
                .port(port)
                .when()
                .get("/api/coffees/{coffeeId}", coffeeId)
                .then()
                .statusCode(200)
                .body("availableQuantity", org.hamcrest.Matchers.equalTo(0));
    }

    private int placeOneItemOrder(long coffeeId) {
        return given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(Map.of("items", List.of(Map.of("coffeeId", coffeeId, "quantity", 1))))
                .when()
                .post("/api/orders")
                .getStatusCode();
    }

    private int getStatusCode(Future<Integer> outcome) {
        try {
            return outcome.get();
        } catch (Exception exception) {
            throw new AssertionError("Concurrent request did not complete.", exception);
        }
    }
}
