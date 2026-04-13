package com.guc.telecom.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Subscription — records that a customer is subscribed to a service plan.
 *
 * Maps to the `subscriptions` table.
 *
 * Key design decisions:
 *
 * 1. Unique constraint on (customer_id, plan_id): prevents duplicate subscriptions
 *    at the database level — a belt-and-suspenders guard on top of the service-layer check.
 *
 * 2. idempotencyKey: stores the client-supplied X-Idempotency-Key header value.
 *    When a client retries an activation request (e.g. after a network timeout),
 *    we look up this key in Redis first. If found, we return the cached result
 *    without re-executing the subscription logic. This is the standard pattern
 *    used by Stripe, Twilio, and most payment/telecom APIs.
 *
 * 3. status: tracks lifecycle — ACTIVE → SUSPENDED → CANCELLED.
 *    Allows soft cancellation without deleting the record (audit trail).
 */
@Entity
@Table(
    name = "subscriptions",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"customer_id", "plan_id"})
    }
)
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Many subscriptions → one customer. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** Many subscriptions → one service plan. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private ServicePlan plan;

    /** Timestamp when the subscription was activated. */
    @Column(name = "activated_at", nullable = false)
    private LocalDateTime activatedAt;

    /**
     * Subscription lifecycle status.
     * ACTIVE: plan is live and billing.
     * SUSPENDED: temporarily paused (e.g. non-payment).
     * CANCELLED: terminated, kept for audit history.
     */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    /**
     * Client-supplied idempotency key (from X-Idempotency-Key header).
     * Stored for audit purposes; primary dedup check is done via Redis.
     * Unique index ensures no two subscriptions share the same key.
     */
    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    // ===== Getters & Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public ServicePlan getPlan() { return plan; }
    public void setPlan(ServicePlan plan) { this.plan = plan; }

    public LocalDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(LocalDateTime activatedAt) { this.activatedAt = activatedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
