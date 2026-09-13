package com.ykalcheu.coffee.app.order.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Test
    void shouldPlaceAndRetrieveOrderWithInventorySnapshot() throws Exception {
        String orderPath = placeOrder("""
                {"items":[{"coffeeId":1,"quantity":2}]}
                """);

        mockMvc.perform(get(orderPath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.totalAmount").value(7.00))
                .andExpect(jsonPath("$.items[0].coffeeName").value("Espresso"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(3.50))
                .andExpect(jsonPath("$.items[0].quantity").value(2));

        mockMvc.perform(get("/api/coffees/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(22));
    }

    @Test
    void shouldReplayTheSameOrderWithoutDecrementingInventoryTwice() throws Exception {
        String request = """
                {"items":[{"coffeeId":1,"quantity":2}]}
                """;

        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "replay-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "replay-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.id").value(1));
        mockMvc.perform(get("/api/coffees/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(22));
    }

    @Test
    void shouldRejectAChangedRequestWithAnExistingIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "reused-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"coffeeId\":1,\"quantity\":1}]}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "reused-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"coffeeId\":1,\"quantity\":2}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void shouldRequireANonBlankIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"coffeeId\":1,\"quantity\":1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));
        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", " ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"coffeeId\":1,\"quantity\":1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));
    }

    @Test
    void shouldRejectInvalidOrderWithoutChangingInventory() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "invalid-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"coffeeId":2,"quantity":1},{"coffeeId":999,"quantity":1}]}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COFFEE_NOT_FOUND"));

        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "insufficient-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"coffeeId":2,"quantity":19}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_INVENTORY"));

        mockMvc.perform(get("/api/coffees/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(18));
    }

    @Test
    void shouldRejectDuplicateCoffeeLine() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "duplicate-line")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"coffeeId":1,"quantity":1},{"coffeeId":1,"quantity":2}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_COFFEE_ITEM"));
    }

    @Test
    void shouldRejectMalformedAndInvalidOrderRequestsWithoutChangingInventory() throws Exception {
        List<String> invalidRequests = List.of(
                "{}",
                "{\"items\":null}",
                "{\"items\":[]}",
                "{\"items\":[null]}",
                "{\"items\":[{}]}",
                "{\"items\":[{\"coffeeId\":1,\"quantity\":0}]}",
                "{\"items\":[{\"coffeeId\":1,\"quantity\":-1}]}",
                "{\"items\":[{\"coffeeId\":1,\"quantity\":1.5}]}",
                "{\"items\":[{\"coffeeId\":1.5,\"quantity\":1}]}",
                "{\"items\":[{\"coffeeId\":1,\"quantity\":\"1\"}]}",
                "{\"items\":[{\"coffeeId\":1,\"quantity\":2147483648}]}",
                "{invalid"
        );

        for (String request : invalidRequests) {
            mockMvc.perform(post("/api/orders")
                            .header("Idempotency-Key", "invalid-payload")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        mockMvc.perform(get("/api/coffees/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(24));
    }

    @Test
    void shouldRejectAnOrderTotalThatExceedsDatabasePrecisionWithoutChangingInventory() throws Exception {
        String firstCoffee = createCoffee("Maximum Price Coffee", "99999999999999999.99", 2);
        String secondCoffee = createCoffee("Additional Price Coffee", "0.01", 2);
        long firstId = idFromLocation(firstCoffee);
        long secondId = idFromLocation(secondCoffee);

        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "too-large-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", List.of(
                                Map.of("coffeeId", firstId, "quantity", 1),
                                Map.of("coffeeId", secondId, "quantity", 1)
                        )))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORDER_TOTAL_TOO_LARGE"));

        mockMvc.perform(get(firstCoffee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(2));
        mockMvc.perform(get(secondCoffee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(2));
    }

    @Test
    void shouldKeepOrderPlacedWhenRestockingWouldOverflowInventory() throws Exception {
        String coffee = createCoffee("Restock Boundary Coffee", "1.00", 1);
        long coffeeId = idFromLocation(coffee);
        String order = placeOrder(objectMapper.writeValueAsString(Map.of(
                "items", List.of(Map.of("coffeeId", coffeeId, "quantity", 1))
        )));

        mockMvc.perform(put(coffee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Restock Boundary Coffee", "origin", "Brazil", "roastLevel", "DARK",
                                "unitPrice", new BigDecimal("1.00"), "availableQuantity", Integer.MAX_VALUE
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post(order + "/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVENTORY_LIMIT_EXCEEDED"));
        mockMvc.perform(get(order))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLACED"));
        mockMvc.perform(get(coffee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(Integer.MAX_VALUE));
    }

    @Test
    void shouldPreserveOrderSnapshotWhenCatalogEntryChanges() throws Exception {
        String orderPath = placeOrder("""
                {"items":[{"coffeeId":1,"quantity":2}]}
                """);

        mockMvc.perform(put("/api/coffees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated Espresso","origin":"Brazil","roastLevel":"DARK","unitPrice":4.10,"availableQuantity":22}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get(orderPath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(7.00))
                .andExpect(jsonPath("$.items[0].coffeeName").value("Espresso"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(3.50));
    }

    @Test
    void shouldCancelOnceRestoreInventoryAndAllowCatalogDeletion() throws Exception {
        String orderPath = placeOrder("""
                {"items":[{"coffeeId":3,"quantity":2}]}
                """);

        mockMvc.perform(delete("/api/coffees/3"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COFFEE_HAS_ACTIVE_ORDERS"));

        mockMvc.perform(post(orderPath + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/coffees/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(12));

        mockMvc.perform(post(orderPath + "/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ALREADY_CANCELLED"));

        mockMvc.perform(delete("/api/coffees/3"))
                .andExpect(status().isNoContent());
    }

    private String placeOrder(String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "placed-order-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getHeader("Location");
    }

    private String createCoffee(String name, String unitPrice, int availableQuantity) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/coffees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name, "origin", "Brazil", "roastLevel", "DARK",
                                "unitPrice", new BigDecimal(unitPrice), "availableQuantity", availableQuantity
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getHeader("Location");
    }

    private long idFromLocation(String location) {
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }
}
