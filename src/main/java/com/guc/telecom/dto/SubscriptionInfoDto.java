package com.guc.telecom.dto;

import java.time.LocalDateTime;

public class SubscriptionInfoDto {
    private Long subscriptionId;
    private Long customerId;
    private String customerName;
    private Long planId;
    private String planName;
    private LocalDateTime activatedAt;
    private String status;

    public SubscriptionInfoDto() {}

    public SubscriptionInfoDto(Long subscriptionId, Long customerId, String customerName,
                                Long planId, String planName, LocalDateTime activatedAt, String status) {
        this.subscriptionId = subscriptionId;
        this.customerId = customerId;
        this.customerName = customerName;
        this.planId = planId;
        this.planName = planName;
        this.activatedAt = activatedAt;
        this.status = status;
    }

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
    public LocalDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(LocalDateTime activatedAt) { this.activatedAt = activatedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
