package com.guc.telecom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CreateCustomerProfileRequest {

    private String billingAddress;

    /** STANDARD, PREMIUM, or VIP */
    @Pattern(regexp = "STANDARD|PREMIUM|VIP", message = "accountTier must be STANDARD, PREMIUM, or VIP")
    private String accountTier;

    /** EMAIL, SMS, or PUSH */
    @Pattern(regexp = "EMAIL|SMS|PUSH", message = "preferredContact must be EMAIL, SMS, or PUSH")
    private String preferredContact;

    public String getBillingAddress() { return billingAddress; }
    public void setBillingAddress(String billingAddress) { this.billingAddress = billingAddress; }
    public String getAccountTier() { return accountTier; }
    public void setAccountTier(String accountTier) { this.accountTier = accountTier; }
    public String getPreferredContact() { return preferredContact; }
    public void setPreferredContact(String preferredContact) { this.preferredContact = preferredContact; }
}
