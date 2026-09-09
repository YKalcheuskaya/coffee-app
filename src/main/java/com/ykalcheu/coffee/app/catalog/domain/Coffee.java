package com.ykalcheu.coffee.app.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

@Entity
@Table(name = "coffee")
public class Coffee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "normalized_name", nullable = false, unique = true, length = 120)
    private String normalizedName;

    @Column(nullable = false, length = 120)
    private String origin;

    @Column(name = "roast_level", nullable = false, length = 32)
    private String roastLevel;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    protected Coffee() {
    }

    private Coffee(String name, String origin, String roastLevel, BigDecimal unitPrice, int availableQuantity) {
        apply(name, origin, roastLevel, unitPrice, availableQuantity);
    }

    public static Coffee create(String name, String origin, String roastLevel, BigDecimal unitPrice, int availableQuantity) {
        return new Coffee(name, origin, roastLevel, unitPrice, availableQuantity);
    }

    public void update(String name, String origin, String roastLevel, BigDecimal unitPrice, int availableQuantity) {
        apply(name, origin, roastLevel, unitPrice, availableQuantity);
    }

    public static String normalizeName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getOrigin() {
        return origin;
    }

    public String getRoastLevel() {
        return roastLevel;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    private void apply(String name, String origin, String roastLevel, BigDecimal unitPrice, int availableQuantity) {
        this.name = name.trim();
        this.normalizedName = normalizeName(name);
        this.origin = origin.trim();
        this.roastLevel = roastLevel.trim();
        this.unitPrice = unitPrice.setScale(2, RoundingMode.UNNECESSARY);
        this.availableQuantity = availableQuantity;
    }
}
