package com.ykalcheu.coffee.app.acceptance;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.concurrent.TimeUnit;
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

    @Autowired
    private DataSource dataSource;

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

    @Test
    void shouldRestoreInventoryExactlyOnceWhenCancellationsOverlap() throws Exception {
        long coffeeId = given().port(port).contentType(ContentType.JSON)
                .body(Map.of("name", "Concurrent Cancellation Coffee", "origin", "Brazil",
                        "roastLevel", "DARK", "unitPrice", 4, "availableQuantity", 1))
                .post("/api/coffees").then().statusCode(201).extract().jsonPath().getLong("id");
        long orderId = given().port(port).contentType(ContentType.JSON)
                .body(Map.of("items", List.of(Map.of("coffeeId", coffeeId, "quantity", 1))))
                .post("/api/orders").then().statusCode(201).extract().jsonPath().getLong("id");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Response> first;
            Future<Response> second;
            // Hold the row until both real HTTP requests reach the lock. A start barrier alone
            // would allow serial database execution and miss the stale-state regression.
            try (Connection blocker = dataSource.getConnection()) {
                blocker.setAutoCommit(false);
                try (var statement = blocker.prepareStatement(
                        "select id from customer_order where id = ? for update")) {
                    statement.setLong(1, orderId);
                    statement.executeQuery().close();
                }
                first = executor.submit(() -> cancelOrder(orderId));
                second = executor.submit(() -> cancelOrder(orderId));
                try {
                    awaitBothCancellationLocks();
                } finally {
                    blocker.rollback();
                }
            }

            Response firstResponse = first.get(20, TimeUnit.SECONDS);
            Response secondResponse = second.get(20, TimeUnit.SECONDS);
            int inventory;
            String orderStatus;
            // Inspect persistence independently of the API response mapping.
            try (Connection observer = dataSource.getConnection();
                 var statement = observer.prepareStatement(
                         "select c.available_quantity, o.status from coffee c cross join customer_order o "
                                 + "where c.id = ? and o.id = ?")) {
                statement.setLong(1, coffeeId);
                statement.setLong(2, orderId);
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    inventory = rows.getInt(1);
                    orderStatus = rows.getString(2);
                }
            }
            org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(List.of(firstResponse.statusCode(), secondResponse.statusCode()))
                        .as("overlapping cancellation HTTP outcomes").containsExactlyInAnyOrder(200, 409);
                softly.assertThat(inventory).as("inventory restored exactly once").isEqualTo(1);
                softly.assertThat(orderStatus).isEqualTo("CANCELLED");
            });
            Response conflict = firstResponse.statusCode() == 409 ? firstResponse : secondResponse;
            assertThat(conflict.jsonPath().getString("code")).isEqualTo("ORDER_ALREADY_CANCELLED");
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(25, TimeUnit.SECONDS))
                    .as("cancellation workers terminate within their HTTP timeout").isTrue();
        }
    }

    private Response cancelOrder(long orderId) {
        return given().port(port)
                .config(io.restassured.RestAssured.config().httpClient(
                        io.restassured.config.HttpClientConfig.httpClientConfig()
                                .setParam("http.connection.timeout", 5000)
                                .setParam("http.socket.timeout", 20000)))
                .post("/api/orders/{orderId}/cancel", orderId);
    }

    private void awaitBothCancellationLocks() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        int waiting = 0;
        try (Connection observer = dataSource.getConnection();
             var statement = observer.prepareStatement(
                     "select count(*) from pg_stat_activity where datname = current_database() "
                             + "and wait_event_type = 'Lock' and query like '%customer_order%'")) {
            statement.setQueryTimeout(3);
            while (System.nanoTime() < deadline) {
                try (var rows = statement.executeQuery()) {
                    rows.next();
                    waiting = rows.getInt(1);
                }
                if (waiting == 2) {
                    return;
                }
                Thread.sleep(25);
            }
        }
        assertThat(waiting).as("both cancellation requests must overlap at the order lock").isEqualTo(2);
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
