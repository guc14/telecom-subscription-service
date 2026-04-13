package com.guc.telecom.dto;

public class CustomerProfileDto {
    private Long id;
    private Long customerId;
    private String billingAddress;
    private String accountTier;
    private String preferredContact;

    public CustomerProfileDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getBillingAddress() { return billingAddress; }
    public void setBillingAddress(String billingAddress) { this.billingAddress = billingAddress; }
    public String getAccountTier() { return accountTier; }
    public void setAccountTier(String accountTier) { this.accountTier = accountTier; }
    public String getPreferredContact() { return preferredContact; }
    public void setPreferredContact(String preferredContact) { this.preferredContact = preferredContact; }
}
