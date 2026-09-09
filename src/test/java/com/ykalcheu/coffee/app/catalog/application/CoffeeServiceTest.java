package com.ykalcheu.coffee.app.catalog.application;

import com.ykalcheu.coffee.app.catalog.api.CreateCoffeeRequest;
import com.ykalcheu.coffee.app.catalog.api.UpdateCoffeeRequest;
import com.ykalcheu.coffee.app.catalog.domain.Coffee;
import com.ykalcheu.coffee.app.catalog.domain.CoffeeHasActiveOrdersException;
import com.ykalcheu.coffee.app.catalog.domain.CoffeeNameAlreadyExistsException;
import com.ykalcheu.coffee.app.catalog.persistence.CoffeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoffeeServiceTest {

    @Mock
    private CoffeeRepository coffeeRepository;

    @Mock
    private ActiveOrderChecker activeOrderChecker;

    private CoffeeService coffeeService;

    @BeforeEach
    void setUp() {
        coffeeService = new CoffeeService(coffeeRepository, activeOrderChecker);
    }

    @Test
    void shouldCreateCoffeeWhenNormalizedNameIsAvailable() {
        when(coffeeRepository.existsByNormalizedName("cold brew")).thenReturn(false);
        when(coffeeRepository.save(any(Coffee.class))).thenAnswer(invocation -> {
            Coffee coffee = invocation.getArgument(0);
            ReflectionTestUtils.setField(coffee, "id", 11L);
            return coffee;
        });

        var response = coffeeService.create(new CreateCoffeeRequest(
                " Cold Brew ", "Colombia", "MEDIUM", new BigDecimal("4.75"), 10
        ));

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.name()).isEqualTo("Cold Brew");
        assertThat(response.unitPrice()).isEqualByComparingTo("4.75");
    }

    @Test
    void shouldRejectDuplicateNameIgnoringCaseBeforeSaving() {
        when(coffeeRepository.existsByNormalizedName("espresso")).thenReturn(true);

        assertThatThrownBy(() -> coffeeService.create(new CreateCoffeeRequest(
                "ESPRESSO", "Italy", "DARK", new BigDecimal("3.50"), 1
        ))).isInstanceOf(CoffeeNameAlreadyExistsException.class);

        verify(coffeeRepository, never()).save(any(Coffee.class));
    }

    @Test
    void shouldReplaceAllMutableCoffeeFields() {
        Coffee coffee = coffee(4L, "Espresso", "Italy", "DARK", "3.50", 5);
        when(coffeeRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(coffee));
        when(coffeeRepository.existsByNormalizedNameAndIdNot("flat white", 4L)).thenReturn(false);

        var response = coffeeService.update(4L, new UpdateCoffeeRequest(
                "Flat White", "Australia", "MEDIUM", new BigDecimal("4.25"), 9
        ));

        assertThat(response.name()).isEqualTo("Flat White");
        assertThat(response.origin()).isEqualTo("Australia");
        assertThat(response.availableQuantity()).isEqualTo(9);
    }

    @Test
    void shouldRejectDeletingCoffeeWithAnActiveOrder() {
        Coffee coffee = coffee(4L, "Espresso", "Italy", "DARK", "3.50", 5);
        when(coffeeRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(coffee));
        when(activeOrderChecker.hasActiveOrdersForCoffee(4L)).thenReturn(true);

        assertThatThrownBy(() -> coffeeService.delete(4L))
                .isInstanceOf(CoffeeHasActiveOrdersException.class);

        verify(coffeeRepository, never()).delete(any(Coffee.class));
    }

    @Test
    void shouldDeleteCoffeeWithoutAnActiveOrder() {
        Coffee coffee = coffee(4L, "Espresso", "Italy", "DARK", "3.50", 5);
        when(coffeeRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(coffee));
        when(activeOrderChecker.hasActiveOrdersForCoffee(4L)).thenReturn(false);

        coffeeService.delete(4L);

        verify(coffeeRepository).delete(coffee);
    }

    private Coffee coffee(long id, String name, String origin, String roastLevel, String price, int quantity) {
        Coffee coffee = Coffee.create(name, origin, roastLevel, new BigDecimal(price), quantity);
        ReflectionTestUtils.setField(coffee, "id", id);
        return coffee;
    }
}
