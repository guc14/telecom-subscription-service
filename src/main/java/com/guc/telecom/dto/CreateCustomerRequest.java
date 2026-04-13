package com.guc.telecom.dto;

import jakarta.validation.constraints.*;

public class CreateCustomerRequest {

    @NotBlank(message = "Name must not be blank")
    @Size(max = 100)
    private String name;

    @NotNull(message = "Age is required")
    @Min(value = 1, message = "Age must be at least 1")
    @Max(value = 150, message = "Age must be at most 150")
    private Integer age;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
}
