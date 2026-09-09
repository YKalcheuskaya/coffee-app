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
import com.ykalcheu.coffee.app.order.domain.OrderAlreadyCancelledException;
import com.ykalcheu.coffee.app.order.persistence.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-09T18:00:00Z"), ZoneOffset.UTC);

    @Mock
    private CoffeeRepository coffeeRepository;

    @Mock
    private OrderRepository orderRepository;

    @Captor
    private ArgumentCaptor<CoffeeOrder> orderCaptor;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(coffeeRepository, orderRepository, FIXED_CLOCK);
    }

    @Test
    void shouldPlaceOrderFromLockedInventoryAndSnapshotCurrentPrice() {
        Coffee espresso = coffee(1L, "Espresso", "3.50", 5);
        when(coffeeRepository.findAllByIdInForUpdate(anyCollection())).thenReturn(List.of(espresso));
        when(orderRepository.save(any(CoffeeOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.place(new PlaceOrderRequest(List.of(new OrderItemRequest(1L, 2))));

        assertThat(response.status().name()).isEqualTo("PLACED");
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-09-09T18:00:00Z"));
        assertThat(response.totalAmount()).isEqualByComparingTo("7.00");
        assertThat(response.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.coffeeName()).isEqualTo("Espresso");
                    assertThat(item.unitPrice()).isEqualByComparingTo("3.50");
                    assertThat(item.quantity()).isEqualTo(2);
                });
        assertThat(espresso.getAvailableQuantity()).isEqualTo(3);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getItems()).hasSize(1);
    }

    @Test
    void shouldRejectDuplicateCoffeeLineBeforeLockingInventory() {
        PlaceOrderRequest request = new PlaceOrderRequest(List.of(
                new OrderItemRequest(1L, 1), new OrderItemRequest(1L, 2)
        ));

        assertThatThrownBy(() -> orderService.place(request))
                .isInstanceOf(DuplicateCoffeeItemException.class);

        verify(coffeeRepository, never()).findAllByIdInForUpdate(anyCollection());
        verify(orderRepository, never()).save(any(CoffeeOrder.class));
    }

    @Test
    void shouldRejectMissingCoffeeBeforeCreatingOrder() {
        when(coffeeRepository.findAllByIdInForUpdate(anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.place(new PlaceOrderRequest(List.of(new OrderItemRequest(99L, 1)))))
                .isInstanceOf(CoffeeNotFoundException.class);

        verify(orderRepository, never()).save(any(CoffeeOrder.class));
    }

    @Test
    void shouldRejectOrderWhenInventoryIsInsufficient() {
        Coffee espresso = coffee(1L, "Espresso", "3.50", 1);
        when(coffeeRepository.findAllByIdInForUpdate(anyCollection())).thenReturn(List.of(espresso));

        assertThatThrownBy(() -> orderService.place(new PlaceOrderRequest(List.of(new OrderItemRequest(1L, 2)))))
                .isInstanceOf(InsufficientInventoryException.class);

        assertThat(espresso.getAvailableQuantity()).isEqualTo(1);
        verify(orderRepository, never()).save(any(CoffeeOrder.class));
    }

    @Test
    void shouldCancelPlacedOrderOnceAndRestoreInventory() {
        Coffee espresso = coffee(1L, "Espresso", "3.50", 3);
        CoffeeOrder order = CoffeeOrder.placed(Instant.parse("2026-09-09T17:00:00Z"));
        ReflectionTestUtils.setField(order, "id", 8L);
        order.addItem(com.ykalcheu.coffee.app.order.domain.OrderItem.from(espresso, 2, order));
        when(orderRepository.findByIdWithItemsForUpdate(8L)).thenReturn(Optional.of(order));
        when(coffeeRepository.findAllByIdInForUpdate(anyCollection())).thenReturn(List.of(espresso));

        OrderResponse response = orderService.cancel(8L);

        assertThat(response.status().name()).isEqualTo("CANCELLED");
        assertThat(espresso.getAvailableQuantity()).isEqualTo(5);
        assertThatThrownBy(() -> orderService.cancel(8L))
                .isInstanceOf(OrderAlreadyCancelledException.class);
        verify(orderRepository, org.mockito.Mockito.times(2)).findByIdWithItemsForUpdate(8L);
    }

    private Coffee coffee(long id, String name, String unitPrice, int quantity) {
        Coffee coffee = Coffee.create(name, "Brazil", "DARK", new BigDecimal(unitPrice), quantity);
        ReflectionTestUtils.setField(coffee, "id", id);
        return coffee;
    }
}
