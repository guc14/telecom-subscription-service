package com.guc.telecom.entity;

import jakarta.persistence.*;
import java.util.List;

/**
 * Customer — represents a telecom subscriber.
 *
 * Maps to the `customers` table.
 * One customer can have one CustomerProfile (billing address, tier, etc.)
 * and multiple Subscriptions (one per ServicePlan they are enrolled in).
 */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Full name of the customer. */
    @Column(nullable = false, length = 100)
    private String name;

    /** Age — used for plan eligibility checks (e.g. senior discounts). */
    private Integer age;

    /** One-to-one: each customer has one billing/account profile. */
    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL)
    private CustomerProfile profile;

    /** One-to-many: a customer can subscribe to multiple service plans. */
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL)
    private List<Subscription> subscriptions;

    // ===== Getters & Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public CustomerProfile getProfile() { return profile; }
    public void setProfile(CustomerProfile profile) { this.profile = profile; }

    public List<Subscription> getSubscriptions() { return subscriptions; }
    public void setSubscriptions(List<Subscription> subscriptions) { this.subscriptions = subscriptions; }
}
