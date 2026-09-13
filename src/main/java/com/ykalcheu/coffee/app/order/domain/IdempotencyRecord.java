package com.ykalcheu.coffee.app.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Durable record that binds one order submission to its canonical request fingerprint.
 * The record and order are committed in one transaction, so a successful key never points to a partial order.
 */
@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 120)
    private String key;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @OneToOne
    @JoinColumn(name = "order_id", unique = true)
    private CoffeeOrder order;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
    }

    private IdempotencyRecord(String key, String requestFingerprint, Instant createdAt) {
        this.key = key;
        this.requestFingerprint = requestFingerprint;
        this.createdAt = createdAt;
    }

    public static IdempotencyRecord claim(String key, String requestFingerprint, Instant createdAt) {
        return new IdempotencyRecord(key, requestFingerprint, createdAt);
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public CoffeeOrder getOrder() {
        return order;
    }

    public void complete(CoffeeOrder order) {
        this.order = order;
    }
}
