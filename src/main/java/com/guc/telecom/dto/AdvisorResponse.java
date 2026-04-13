package com.guc.telecom.dto;

import java.util.List;

public class AdvisorResponse {
    private Long customerId;
    private String customerName;
    private String advice;
    private List<String> recommendedPlans;
    private Integer agentSteps;

    public AdvisorResponse() {}

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getAdvice() { return advice; }
    public void setAdvice(String advice) { this.advice = advice; }
    public List<String> getRecommendedPlans() { return recommendedPlans; }
    public void setRecommendedPlans(List<String> recommendedPlans) { this.recommendedPlans = recommendedPlans; }
    public Integer getAgentSteps() { return agentSteps; }
    public void setAgentSteps(Integer agentSteps) { this.agentSteps = agentSteps; }
}
