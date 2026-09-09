package com.ykalcheu.coffee.app.catalog.api;

import com.ykalcheu.coffee.app.catalog.application.CoffeeService;
import com.ykalcheu.coffee.app.catalog.domain.CoffeeNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CoffeeController.class)
class CoffeeControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CoffeeService coffeeService;

    @Test
    void shouldReturnCreatedCatalogResourceAndLocation() throws Exception {
        when(coffeeService.create(any())).thenReturn(new CoffeeResponse(
                42L, "Cold Brew", "Colombia", "MEDIUM", new java.math.BigDecimal("4.75"), 10
        ));

        mockMvc.perform(post("/api/coffees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cold Brew","origin":"Colombia","roastLevel":"MEDIUM","unitPrice":4.75,"availableQuantity":10}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/coffees/42")))
                .andExpect(jsonPath("$.name").value("Cold Brew"));
    }

    @Test
    void shouldMapReplacementRequestAndReturnUpdatedResource() throws Exception {
        when(coffeeService.update(org.mockito.ArgumentMatchers.eq(42L), any())).thenReturn(new CoffeeResponse(
                42L, "Flat White", "Australia", "MEDIUM", new java.math.BigDecimal("4.25"), 7
        ));

        mockMvc.perform(put("/api/coffees/42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Flat White","origin":"Australia","roastLevel":"MEDIUM","unitPrice":4.25,"availableQuantity":7}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.availableQuantity").value(7));
    }

    @Test
    void shouldReturnStructuredValidationErrorWithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/coffees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","origin":"","roastLevel":"","unitPrice":-1,"availableQuantity":-1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void shouldMapDomainNotFoundExceptionToApiError() throws Exception {
        doThrow(new CoffeeNotFoundException(25L)).when(coffeeService).delete(25L);

        mockMvc.perform(delete("/api/coffees/25"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COFFEE_NOT_FOUND"))
                .andExpect(jsonPath("$.details.coffeeId").value(25));
    }
}
