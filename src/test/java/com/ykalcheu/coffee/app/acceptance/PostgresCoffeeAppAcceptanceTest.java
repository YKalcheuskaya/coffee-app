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

@Testcontainers
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

    @Test
    void shouldReturnConflictWhenConcurrentCreateLosesNormalizedNameRace() throws Exception {
        String normalizedName = "concurrent duplicate coffee";
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.prepareStatement(
                    "insert into coffee (name, normalized_name, origin, roast_level, unit_price, available_quantity) "
                            + "values (?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, "Concurrent Duplicate Coffee");
                statement.setString(2, normalizedName);
                statement.setString(3, "Brazil");
                statement.setString(4, "DARK");
                statement.setBigDecimal(5, new java.math.BigDecimal("4.00"));
                statement.setInt(6, 1);
                statement.executeUpdate();
            }

            Future<Response> competingRequest = executor.submit(() -> given().port(port)
                    .contentType(ContentType.JSON)
                    .body(Map.of("name", " Concurrent Duplicate Coffee ", "origin", "Colombia",
                            "roastLevel", "MEDIUM", "unitPrice", 4, "availableQuantity", 2))
                    .post("/api/coffees"));
            awaitWaitingCoffeeInsert();
            blocker.commit();

            Response response = competingRequest.get(20, TimeUnit.SECONDS);
            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(response.jsonPath().getString("code")).isEqualTo("COFFEE_NAME_ALREADY_EXISTS");
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(25, TimeUnit.SECONDS))
                    .as("duplicate-create worker terminates within its HTTP timeout").isTrue();
        }

        try (Connection observer = dataSource.getConnection();
             var statement = observer.prepareStatement(
                     "select count(*) from coffee where normalized_name = ?")) {
            statement.setString(1, normalizedName);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void shouldRollbackEveryInventoryChangeWhenOneOrderLineIsInsufficient() throws Exception {
        long availableCoffeeId = given().port(port).contentType(ContentType.JSON)
                .body(Map.of("name", "Rollback Available Coffee", "origin", "Brazil",
                        "roastLevel", "DARK", "unitPrice", 4, "availableQuantity", 5))
                .post("/api/coffees").then().statusCode(201).extract().jsonPath().getLong("id");
        long limitedCoffeeId = given().port(port).contentType(ContentType.JSON)
                .body(Map.of("name", "Rollback Limited Coffee", "origin", "Brazil",
                        "roastLevel", "DARK", "unitPrice", 4, "availableQuantity", 1))
                .post("/api/coffees").then().statusCode(201).extract().jsonPath().getLong("id");
        long ordersBefore = countRows("customer_order");
        long itemsBefore = countRows("order_item");

        given().port(port).contentType(ContentType.JSON)
                .body(Map.of("items", List.of(
                        Map.of("coffeeId", availableCoffeeId, "quantity", 1),
                        Map.of("coffeeId", limitedCoffeeId, "quantity", 2)
                )))
                .post("/api/orders")
                .then().statusCode(409).body("code", org.hamcrest.Matchers.equalTo("INSUFFICIENT_INVENTORY"));

        assertThat(stock(availableCoffeeId)).as("inventory changed before the failure is rolled back").isEqualTo(5);
        assertThat(stock(limitedCoffeeId)).isEqualTo(1);
        assertThat(countRows("customer_order")).isEqualTo(ordersBefore);
        assertThat(countRows("order_item")).isEqualTo(itemsBefore);
    }

    @Test
    void shouldKeepCancelledOrderSnapshotsAfterCatalogDeletion() throws Exception {
        long coffeeId = given().port(port).contentType(ContentType.JSON)
                .body(Map.of("name", "Historical Snapshot Coffee", "origin", "Ethiopia",
                        "roastLevel", "LIGHT", "unitPrice", 4.25, "availableQuantity", 1))
                .post("/api/coffees").then().statusCode(201).extract().jsonPath().getLong("id");
        long orderId = given().port(port).contentType(ContentType.JSON)
                .body(Map.of("items", List.of(Map.of("coffeeId", coffeeId, "quantity", 1))))
                .post("/api/orders").then().statusCode(201).extract().jsonPath().getLong("id");

        given().port(port).post("/api/orders/{orderId}/cancel", orderId)
                .then().statusCode(200).body("status", org.hamcrest.Matchers.equalTo("CANCELLED"));
        given().port(port).delete("/api/coffees/{coffeeId}", coffeeId).then().statusCode(204);
        given().port(port).get("/api/orders/{orderId}", orderId)
                .then().statusCode(200)
                .body("status", org.hamcrest.Matchers.equalTo("CANCELLED"))
                .body("items[0].coffeeId", org.hamcrest.Matchers.equalTo((int) coffeeId))
                .body("items[0].coffeeName", org.hamcrest.Matchers.equalTo("Historical Snapshot Coffee"))
                .body("items[0].unitPrice", org.hamcrest.Matchers.equalTo(4.25f));

        try (Connection observer = dataSource.getConnection();
             var statement = observer.prepareStatement(
                     "select count(*) from order_item where order_id = ? and coffee_id = ?")) {
            statement.setLong(1, orderId);
            statement.setLong(2, coffeeId);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
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

    private void awaitWaitingCoffeeInsert() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        int waiting = 0;
        try (Connection observer = dataSource.getConnection();
             var statement = observer.prepareStatement(
                     "select count(*) from pg_stat_activity where datname = current_database() "
                             + "and wait_event_type = 'Lock' and query like '%insert into coffee%'")) {
            statement.setQueryTimeout(3);
            while (System.nanoTime() < deadline) {
                try (var rows = statement.executeQuery()) {
                    rows.next();
                    waiting = rows.getInt(1);
                }
                if (waiting == 1) {
                    return;
                }
                Thread.sleep(25);
            }
        }
        assertThat(waiting).as("competing create request must reach the unique-index lock").isEqualTo(1);
    }

    private long countRows(String table) throws Exception {
        try (Connection observer = dataSource.getConnection();
             var statement = observer.createStatement();
             var rows = statement.executeQuery("select count(*) from " + table)) {
            assertThat(rows.next()).isTrue();
            return rows.getLong(1);
        }
    }

    private int stock(long coffeeId) throws Exception {
        try (Connection observer = dataSource.getConnection();
             var statement = observer.prepareStatement("select available_quantity from coffee where id = ?")) {
            statement.setLong(1, coffeeId);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getInt(1);
            }
        }
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
