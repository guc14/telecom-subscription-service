package com.guc.telecom.dto;

import java.util.List;

public class AdvisorRequest {
    private Long customerId;
    private String customerName;
    private Integer age;
    private String question;

    public AdvisorRequest() {}
    public AdvisorRequest(Long customerId, String customerName, Integer age, String question) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.age = age;
        this.question = question;
    }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
}
