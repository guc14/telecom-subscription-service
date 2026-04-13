package com.guc.telecom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class CreateServicePlanRequest {

    @NotBlank(message = "Plan name must not be blank")
    @Size(max = 100)
    private String name;

    /** Optional — if blank, AI will generate a description automatically. */
    @Size(max = 500)
    private String description;

    /** Monthly fee in CAD cents, e.g. 6500 = $65.00/month. */
    @Positive(message = "Monthly fee must be positive")
    private Integer monthlyFeeCents;

    /** null = unlimited subscribers. */
    @Positive(message = "Capacity must be positive if set")
    private Integer capacity;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getMonthlyFeeCents() { return monthlyFeeCents; }
    public void setMonthlyFeeCents(Integer monthlyFeeCents) { this.monthlyFeeCents = monthlyFeeCents; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
}
