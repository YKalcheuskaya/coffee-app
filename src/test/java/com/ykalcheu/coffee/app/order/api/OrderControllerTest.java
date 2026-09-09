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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
    void shouldRejectInvalidOrderWithoutChangingInventory() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"coffeeId":2,"quantity":1},{"coffeeId":999,"quantity":1}]}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COFFEE_NOT_FOUND"));

        mockMvc.perform(post("/api/orders")
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"coffeeId":1,"quantity":1},{"coffeeId":1,"quantity":2}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_COFFEE_ITEM"));
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getHeader("Location");
    }
}
