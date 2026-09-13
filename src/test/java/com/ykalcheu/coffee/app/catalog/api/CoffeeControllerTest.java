package com.ykalcheu.coffee.app.catalog.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CoffeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldListSeededCoffeeCatalog() throws Exception {
        mockMvc.perform(get("/api/coffees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Espresso"))
                .andExpect(jsonPath("$[2].id").value(3));
    }

    @Test
    void shouldCreateCoffeeCatalogItem() throws Exception {
        mockMvc.perform(post("/api/coffees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cold Brew","origin":"Colombia","roastLevel":"MEDIUM","unitPrice":4.75,"availableQuantity":10}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/coffees/4")))
                .andExpect(jsonPath("$.name").value("Cold Brew"))
                .andExpect(jsonPath("$.unitPrice").value(4.75));
    }

    @Test
    void shouldReplaceCoffeeCatalogItem() throws Exception {
        mockMvc.perform(put("/api/coffees/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated Flat White","origin":"Australia","roastLevel":"DARK","unitPrice":4.60,"availableQuantity":20}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Flat White"))
                .andExpect(jsonPath("$.availableQuantity").value(20));
    }

    @Test
    void shouldDeleteCoffeeCatalogItem() throws Exception {
        mockMvc.perform(delete("/api/coffees/3"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/coffees/3"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COFFEE_NOT_FOUND"));
    }

    @Test
    void shouldRejectDuplicateCoffeeNameIgnoringCase() throws Exception {
        mockMvc.perform(post("/api/coffees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"espresso","origin":"Brazil","roastLevel":"MEDIUM","unitPrice":4.00,"availableQuantity":8}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COFFEE_NAME_ALREADY_EXISTS"));
    }

    @Test
    void shouldReturnValidationErrorForInvalidCoffeeRequest() throws Exception {
        mockMvc.perform(post("/api/coffees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","origin":"Italy","roastLevel":"DARK","unitPrice":-1.00,"availableQuantity":-1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void shouldUseApiErrorContractForFrameworkRequestFailures() throws Exception {
        mockMvc.perform(get("/api/coffees/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("coffeeId"));

        mockMvc.perform(post("/api/coffees")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("coffee"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));

        mockMvc.perform(delete("/api/coffees"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

        mockMvc.perform(get("/api/not-a-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldExposeGeneratedOpenApiAndSwaggerUi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Coffee App API"))
                .andExpect(jsonPath("$.paths['/api/coffees']").exists())
                .andExpect(jsonPath("$.paths['/api/coffees'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/orders'].post.responses['409']").exists())
                .andExpect(jsonPath("$.components.schemas.ApiError").exists());

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
