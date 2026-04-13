package com.guc.telecom.entity;

import jakarta.persistence.*;

/**
 * CustomerProfile — extended billing and account information for a customer.
 *
 * Stored separately from Customer to keep the core entity lean.
 * Contains billing address, account tier (STANDARD / PREMIUM / VIP),
 * and preferred contact channel.
 */
@Entity
@Table(name = "customer_profiles")
public class CustomerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Billing address for invoicing. */
    @Column(length = 300)
    private String billingAddress;

    /**
     * Account tier — determines discount eligibility and priority support.
     * Values: STANDARD, PREMIUM, VIP
     */
    @Column(length = 20)
    private String accountTier;

    /** Preferred notification channel: EMAIL, SMS, or PUSH. */
    @Column(length = 10)
    private String preferredContact;

    /** One-to-one back reference to the owning Customer. */
    @OneToOne
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    private Customer customer;

    // ===== Getters & Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBillingAddress() { return billingAddress; }
    public void setBillingAddress(String billingAddress) { this.billingAddress = billingAddress; }

    public String getAccountTier() { return accountTier; }
    public void setAccountTier(String accountTier) { this.accountTier = accountTier; }

    public String getPreferredContact() { return preferredContact; }
    public void setPreferredContact(String preferredContact) { this.preferredContact = preferredContact; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
}
