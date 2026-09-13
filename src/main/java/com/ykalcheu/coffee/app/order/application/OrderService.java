package com.ykalcheu.coffee.app.order.application;

import com.ykalcheu.coffee.app.catalog.domain.Coffee;
import com.ykalcheu.coffee.app.catalog.domain.CoffeeNotFoundException;
import com.ykalcheu.coffee.app.catalog.persistence.CoffeeRepository;
import com.ykalcheu.coffee.app.order.api.OrderItemRequest;
import com.ykalcheu.coffee.app.order.api.OrderResponse;
import com.ykalcheu.coffee.app.order.api.PlaceOrderRequest;
import com.ykalcheu.coffee.app.order.domain.CoffeeOrder;
import com.ykalcheu.coffee.app.order.domain.DuplicateCoffeeItemException;
import com.ykalcheu.coffee.app.order.domain.InsufficientInventoryException;
import com.ykalcheu.coffee.app.order.domain.IdempotencyKeyReusedException;
import com.ykalcheu.coffee.app.order.domain.IdempotencyRecord;
import com.ykalcheu.coffee.app.order.domain.InvalidIdempotencyKeyException;
import com.ykalcheu.coffee.app.order.domain.OrderAlreadyCancelledException;
import com.ykalcheu.coffee.app.order.domain.OrderItem;
import com.ykalcheu.coffee.app.order.domain.OrderNotFoundException;
import com.ykalcheu.coffee.app.order.domain.OrderStatus;
import com.ykalcheu.coffee.app.order.persistence.OrderRepository;
import com.ykalcheu.coffee.app.order.persistence.IdempotencyRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Coordinates transactional order lifecycle rules: inventory reservation, immutable snapshots, and one-time restocking.
 * It owns the lock ordering required for concurrent inventory mutations.
 */
@Service
public class OrderService {

    private final CoffeeRepository coffeeRepository;
    private final OrderRepository orderRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final Clock clock;

    public OrderService(CoffeeRepository coffeeRepository, OrderRepository orderRepository,
                        IdempotencyRecordRepository idempotencyRecordRepository, Clock clock) {
        this.coffeeRepository = coffeeRepository;
        this.orderRepository = orderRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.clock = clock;
    }

    @Transactional
    public OrderPlacementResult place(String idempotencyKey, PlaceOrderRequest request) {
        String key = validateKey(idempotencyKey);
        assertUniqueCoffeeIds(request.items());
        String fingerprint = fingerprint(request.items());

        boolean claimed = idempotencyRecordRepository.claimIfAbsent(key, fingerprint, Instant.now(clock)) == 1;
        IdempotencyRecord existing = idempotencyRecordRepository.findByKeyForUpdate(key)
                .orElseThrow(() -> new IllegalStateException("Idempotency claim was not persisted."));
        if (!claimed && !existing.getRequestFingerprint().equals(fingerprint)) {
            throw new IdempotencyKeyReusedException();
        }
        if (existing.getOrder() != null) {
            return new OrderPlacementResult(OrderResponse.from(existing.getOrder()), true);
        }

        CoffeeOrder order = createOrder(request);
        orderRepository.save(order);
        existing.complete(order);
        return new OrderPlacementResult(OrderResponse.from(order), false);
    }

    private CoffeeOrder createOrder(PlaceOrderRequest request) {
        Map<Long, Coffee> coffeeById = lockAndIndex(request.items().stream().map(OrderItemRequest::coffeeId).toList());

        CoffeeOrder order = CoffeeOrder.placed(Instant.now(clock));
        for (OrderItemRequest requestedItem : request.items()) {
            Coffee coffee = coffeeById.get(requestedItem.coffeeId());
            if (coffee == null) {
                throw new CoffeeNotFoundException(requestedItem.coffeeId());
            }
            if (coffee.getAvailableQuantity() < requestedItem.quantity()) {
                throw new InsufficientInventoryException(
                        coffee.getId(), requestedItem.quantity(), coffee.getAvailableQuantity()
                );
            }

            coffee.decreaseInventory(requestedItem.quantity());
            order.addItem(OrderItem.from(coffee, requestedItem.quantity(), order));
        }

        return order;
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(long orderId) {
        CoffeeOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancel(long orderId) {
        // The order lock prevents two cancellation requests from restoring the same inventory twice.
        CoffeeOrder order = orderRepository.findByIdWithItemsForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new OrderAlreadyCancelledException(orderId);
        }

        Map<Long, Coffee> coffeeById = lockAndIndex(order.getItems().stream().map(item -> item.getCoffeeId()).toList());
        for (OrderItem item : order.getItems()) {
            Coffee coffee = coffeeById.get(item.getCoffeeId());
            if (coffee == null) {
                throw new CoffeeNotFoundException(item.getCoffeeId());
            }
            coffee.increaseInventory(item.getQuantity());
        }
        order.cancel();
        return OrderResponse.from(order);
    }

    private void assertUniqueCoffeeIds(List<OrderItemRequest> items) {
        Set<Long> coffeeIds = new HashSet<>();
        for (OrderItemRequest item : items) {
            if (!coffeeIds.add(item.coffeeId())) {
                throw new DuplicateCoffeeItemException(item.coffeeId());
            }
        }
    }

    private Map<Long, Coffee> lockAndIndex(List<Long> coffeeIds) {
        // Every inventory mutation acquires rows in identifier order to avoid inconsistent lock ordering.
        List<Long> sortedCoffeeIds = coffeeIds.stream().sorted().toList();
        Map<Long, Coffee> coffeeById = new HashMap<>();
        for (Coffee coffee : coffeeRepository.findAllByIdInForUpdate(sortedCoffeeIds)) {
            coffeeById.put(coffee.getId(), coffee);
        }
        return coffeeById;
    }

    private String validateKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 120) {
            throw new InvalidIdempotencyKeyException();
        }
        return idempotencyKey;
    }

    private String fingerprint(List<OrderItemRequest> items) {
        String canonicalRequest = items.stream()
                .sorted(java.util.Comparator.comparing(OrderItemRequest::coffeeId))
                .map(item -> item.coffeeId() + ":" + item.quantity())
                .collect(java.util.stream.Collectors.joining("|"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime.", exception);
        }
    }
}
