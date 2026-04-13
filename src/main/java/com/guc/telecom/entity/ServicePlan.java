package com.guc.telecom.entity;

import jakarta.persistence.*;

/**
 * ServicePlan — a telecom service offering that customers can subscribe to.
 *
 * Examples: "5G Unlimited", "Home Fibre 500M", "International Roaming Add-on".
 *
 * Maps to the `service_plans` table.
 * capacity: max concurrent subscribers (null = unlimited, e.g. for mass-market plans).
 * monthlyFee: base monthly charge in CAD cents (avoids float precision issues).
 */
@Entity
@Table(name = "service_plans")
public class ServicePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Plan name, e.g. "5G Unlimited Plus". */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * AI-generated or manually entered plan description.
     * Populated by the OpenAI integration when description is omitted at creation time.
     */
    @Column(length = 500)
    private String description;

    /**
     * Monthly fee in CAD cents. Stored as integer to avoid floating-point rounding.
     * e.g. 6500 = $65.00/month.
     */
    private Integer monthlyFeeCents;

    /**
     * Maximum number of simultaneous subscribers allowed.
     * null = unlimited (typical for consumer plans).
     * Set a cap for enterprise or pilot plans with limited capacity.
     */
    @Column(name = "capacity")
    private Integer capacity;

    // ===== Getters & Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getMonthlyFeeCents() { return monthlyFeeCents; }
    public void setMonthlyFeeCents(Integer monthlyFeeCents) { this.monthlyFeeCents = monthlyFeeCents; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
}
