package com.ykalcheu.coffee.app.order.domain;

import com.ykalcheu.coffee.app.catalog.domain.Coffee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Historical order line that snapshots coffee name and unit price at purchase time.
 * The stored coffee identifier is intentionally not a foreign key to the live catalog.
 */
@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CoffeeOrder order;

    @Column(name = "coffee_id", nullable = false)
    private Long coffeeId;

    @Column(name = "coffee_name", nullable = false, length = 120)
    private String coffeeName;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal;

    protected OrderItem() {
    }

    public static OrderItem from(Coffee coffee, int quantity, CoffeeOrder order) {
        OrderItem item = new OrderItem();
        item.order = order;
        // Copy catalog data into the order so later catalog changes do not rewrite purchase history.
        item.coffeeId = coffee.getId();
        item.coffeeName = coffee.getName();
        item.unitPrice = coffee.getUnitPrice();
        item.quantity = quantity;
        item.lineTotal = coffee.getUnitPrice().multiply(BigDecimal.valueOf(quantity));
        return item;
    }

    public Long getCoffeeId() {
        return coffeeId;
    }

    public String getCoffeeName() {
        return coffeeName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
