package com.ykalcheu.coffee.app.catalog.api;

import com.ykalcheu.coffee.app.catalog.application.CoffeeService;
import com.ykalcheu.coffee.app.shared.error.ApiError;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/coffees")
@Tag(name = "Coffee catalog", description = "Create, read, update, and delete coffee catalog items.")
public class CoffeeController {

    private final CoffeeService coffeeService;

    public CoffeeController(CoffeeService coffeeService) {
        this.coffeeService = coffeeService;
    }

    @GetMapping
    @Operation(summary = "List coffee catalog items")
    @ApiResponse(responseCode = "200", description = "Catalog items returned")
    public List<CoffeeResponse> listCoffees() {
        return coffeeService.findAll();
    }

    @GetMapping("/{coffeeId}")
    @Operation(summary = "Retrieve a coffee catalog item")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catalog item returned"),
            @ApiResponse(responseCode = "404", description = "Coffee does not exist", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public CoffeeResponse getCoffee(@PathVariable long coffeeId) {
        return coffeeService.findById(coffeeId);
    }

    @PostMapping
    @Operation(summary = "Create a coffee catalog item")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Catalog item created"),
            @ApiResponse(responseCode = "400", description = "Request validation failed", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Coffee name already exists", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<CoffeeResponse> createCoffee(@Valid @RequestBody CreateCoffeeRequest request) {
        CoffeeResponse coffee = coffeeService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{coffeeId}")
                .buildAndExpand(coffee.id())
                .toUri();
        return ResponseEntity.created(location).body(coffee);
    }

    @PutMapping("/{coffeeId}")
    @Operation(summary = "Replace a coffee catalog item")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catalog item replaced"),
            @ApiResponse(responseCode = "400", description = "Request validation failed", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Coffee does not exist", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Coffee name already exists", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public CoffeeResponse updateCoffee(@PathVariable long coffeeId, @Valid @RequestBody UpdateCoffeeRequest request) {
        return coffeeService.update(coffeeId, request);
    }

    @DeleteMapping("/{coffeeId}")
    @Operation(summary = "Delete a coffee catalog item")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Catalog item deleted"),
            @ApiResponse(responseCode = "404", description = "Coffee does not exist", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Coffee belongs to an active order", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> deleteCoffee(@PathVariable long coffeeId) {
        coffeeService.delete(coffeeId);
        return ResponseEntity.noContent().build();
    }
}
