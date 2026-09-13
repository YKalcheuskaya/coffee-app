package com.ykalcheu.coffee.app.acceptance;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CoffeeAppWorkflowHttpTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void configureHttpClient() {
        RestAssured.port = port;
    }

    @Test
    void shouldCompleteCatalogToOrderToCancellationWorkflowThroughHttp() {
        long coffeeId = given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "Seasonal Cold Brew",
                        "origin", "Colombia",
                        "roastLevel", "MEDIUM",
                        "unitPrice", 4.75,
                        "availableQuantity", 4
                ))
                .when()
                .post("/api/coffees")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getLong("id");

        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "Seasonal Cold Brew",
                        "origin", "Colombia",
                        "roastLevel", "DARK",
                        "unitPrice", 5.25,
                        "availableQuantity", 4
                ))
                .when()
                .put("/api/coffees/{coffeeId}", coffeeId)
                .then()
                .statusCode(200)
                .body("unitPrice", equalTo(5.25f));

        long orderId = given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", "workflow-order")
                .body(Map.of("items", List.of(Map.of("coffeeId", coffeeId, "quantity", 2))))
                .when()
                .post("/api/orders")
                .then()
                .statusCode(201)
                .body("status", equalTo("PLACED"))
                .body("totalAmount", equalTo(10.50f))
                .extract()
                .jsonPath()
                .getLong("id");

        given()
                .when()
                .get("/api/orders/{orderId}", orderId)
                .then()
                .statusCode(200)
                .body("items[0].coffeeName", equalTo("Seasonal Cold Brew"))
                .body("items[0].unitPrice", equalTo(5.25f));

        given()
                .when()
                .post("/api/orders/{orderId}/cancel", orderId)
                .then()
                .statusCode(200)
                .body("status", equalTo("CANCELLED"));

        given()
                .when()
                .delete("/api/coffees/{coffeeId}", coffeeId)
                .then()
                .statusCode(204);
    }
}
