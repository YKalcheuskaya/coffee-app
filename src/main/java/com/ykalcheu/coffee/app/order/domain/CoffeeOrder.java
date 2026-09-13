package com.ykalcheu.coffee.app.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Order aggregate that preserves the commercial record after the live catalog changes.
 * Its only lifecycle transition is from {@link OrderStatus#PLACED} to {@link OrderStatus#CANCELLED}.
 */
@Entity
@Table(name = "customer_order")
public class CoffeeOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private static final BigDecimal MAX_TOTAL = new BigDecimal("99999999999999999.99");

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected CoffeeOrder() {
    }

    public static CoffeeOrder placed(Instant createdAt) {
        CoffeeOrder order = new CoffeeOrder();
        order.status = OrderStatus.PLACED;
        order.createdAt = createdAt;
        order.currency = "USD";
        order.totalAmount = BigDecimal.ZERO.setScale(2);
        return order;
    }

    public void addItem(OrderItem item) {
        BigDecimal nextTotal = totalAmount.add(item.getLineTotal());
        if (nextTotal.compareTo(MAX_TOTAL) > 0) {
            throw new AmountLimitExceededException();
        }
        items.add(item);
        totalAmount = nextTotal;
    }

    public static class AmountLimitExceededException extends RuntimeException {
        public AmountLimitExceededException() {
            super("Order total exceeds the supported maximum of 99999999999999999.99 USD.");
        }
    }

    public void cancel() {
        status = OrderStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }
}
