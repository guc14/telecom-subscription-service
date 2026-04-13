package com.guc.telecom.event;

import java.time.LocalDateTime;

/**
 * SubscriptionActivatedEvent — Kafka message published when a customer
 * successfully activates a service plan subscription.
 *
 * Published to topic: subscription.activated
 *
 * Design notes:
 * - Plain POJO (no Kafka-specific imports) — keeps domain model clean.
 * - All fields are primitives/String/LocalDateTime for easy JSON serialisation.
 * - Consumers can use this event to trigger downstream workflows:
 *     • Send welcome SMS / email notification
 *     • Update CRM system
 *     • Trigger billing cycle creation
 *     • Write to audit log
 */
public class SubscriptionActivatedEvent {

    private Long subscriptionId;
    private Long customerId;
    private String customerName;
    private Long planId;
    private String planName;
    private String status;
    private LocalDateTime activatedAt;
    private String idempotencyKey;

    // Default constructor required for Jackson deserialisation
    public SubscriptionActivatedEvent() {}

    public SubscriptionActivatedEvent(Long subscriptionId, Long customerId, String customerName,
                                       Long planId, String planName, String status,
                                       LocalDateTime activatedAt, String idempotencyKey) {
        this.subscriptionId = subscriptionId;
        this.customerId = customerId;
        this.customerName = customerName;
        this.planId = planId;
        this.planName = planName;
        this.status = status;
        this.activatedAt = activatedAt;
        this.idempotencyKey = idempotencyKey;
    }

    // ===== Getters & Setters =====

    public Long getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(Long subscriptionId) { this.subscriptionId = subscriptionId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }

    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(LocalDateTime activatedAt) { this.activatedAt = activatedAt; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    @Override
    public String toString() {
        return "SubscriptionActivatedEvent{" +
               "subscriptionId=" + subscriptionId +
               ", customerId=" + customerId +
               ", planName='" + planName + '\'' +
               ", activatedAt=" + activatedAt +
               '}';
    }
}
