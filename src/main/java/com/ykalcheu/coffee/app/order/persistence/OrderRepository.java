package com.ykalcheu.coffee.app.order.persistence;

import com.ykalcheu.coffee.app.catalog.application.ActiveOrderChecker;
import com.ykalcheu.coffee.app.order.domain.CoffeeOrder;
import com.ykalcheu.coffee.app.order.domain.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * Persists orders, supplies the catalog's active-order policy, and loads the order lock used by cancellation.
 */
public interface OrderRepository extends JpaRepository<CoffeeOrder, Long>, ActiveOrderChecker {

    boolean existsByItemsCoffeeIdAndStatus(long coffeeId, OrderStatus status);

    @Override
    default boolean hasActiveOrdersForCoffee(long coffeeId) {
        return existsByItemsCoffeeIdAndStatus(coffeeId, OrderStatus.PLACED);
    }

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<CoffeeOrder> findById(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct coffeeOrder from CoffeeOrder coffeeOrder left join fetch coffeeOrder.items where coffeeOrder.id = :orderId")
    Optional<CoffeeOrder> findByIdWithItemsForUpdate(long orderId);
}
