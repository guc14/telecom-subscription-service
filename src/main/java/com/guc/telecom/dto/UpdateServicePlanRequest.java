package com.guc.telecom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class UpdateServicePlanRequest {

    @NotBlank(message = "Plan name must not be blank")
    @Size(max = 100)
    private String name;

    @Size(max = 500)
    private String description;

    @Positive
    private Integer monthlyFeeCents;

    @Positive
    private Integer capacity;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getMonthlyFeeCents() { return monthlyFeeCents; }
    public void setMonthlyFeeCents(Integer m) { this.monthlyFeeCents = m; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
}
