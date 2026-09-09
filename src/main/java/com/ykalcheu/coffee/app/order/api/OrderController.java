package com.ykalcheu.coffee.app.order.api;

import com.ykalcheu.coffee.app.order.application.OrderService;
import io.swagger.v3.oas.annotations.Operation;
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
    public OrderResponse getOrder(@PathVariable long orderId) {
        return orderService.findById(orderId);
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel a placed order and restore inventory")
    public OrderResponse cancelOrder(@PathVariable long orderId) {
        return orderService.cancel(orderId);
    }
}
