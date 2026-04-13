package com.guc.telecom.dto;

public class ServicePlanDto {
    private Long id;
    private String name;
    private String description;
    private Integer monthlyFeeCents;
    private Integer capacity;

    public ServicePlanDto() {}

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
