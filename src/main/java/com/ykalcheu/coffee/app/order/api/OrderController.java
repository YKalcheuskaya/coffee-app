package com.ykalcheu.coffee.app.order.api;

import com.ykalcheu.coffee.app.order.application.OrderService;
import com.ykalcheu.coffee.app.shared.error.ApiError;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * HTTP boundary for order placement, retrieval, and cancellation.
 * Orders are lifecycle records, so the API intentionally has no generic update or delete endpoint.
 */
@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Place, retrieve, and cancel transactional coffee orders.")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Place an order")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order placed"),
            @ApiResponse(responseCode = "400", description = "Request validation failed", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Coffee does not exist", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Duplicate line or insufficient inventory", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        OrderResponse order = orderService.place(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{orderId}")
                .buildAndExpand(order.id())
                .toUri();
        return ResponseEntity.created(location).body(order);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Retrieve an order")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order returned"),
            @ApiResponse(responseCode = "404", description = "Order does not exist", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public OrderResponse getOrder(@PathVariable long orderId) {
        return orderService.findById(orderId);
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel a placed order and restore inventory")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order cancelled"),
            @ApiResponse(responseCode = "404", description = "Order does not exist", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Order is already cancelled", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public OrderResponse cancelOrder(@PathVariable long orderId) {
        return orderService.cancel(orderId);
    }
}
