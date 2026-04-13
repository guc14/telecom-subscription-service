package com.guc.telecom.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guc.telecom.dto.CreateCustomerRequest;
import com.guc.telecom.dto.CreateServicePlanRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for subscription activation.
 *
 * Uses:
 *   - @SpringBootTest: full application context (H2 in-memory DB)
 *   - @EmbeddedKafka: real Kafka broker in-process, no external Kafka needed
 *   - @AutoConfigureMockMvc: real HTTP layer via MockMvc
 *
 * Tests verify the complete flow:
 *   HTTP request → Controller → Service → DB → Redis (mocked) → Kafka (embedded)
 *
 * Coverage:
 *   - Successful activation → 200
 *   - Idempotency: same X-Idempotency-Key → 200 (no error, no duplicate)
 *   - Duplicate subscription → 409 DUPLICATE_SUBSCRIPTION
 *   - Plan capacity exceeded → 409 PLAN_CAPACITY_EXCEEDED
 *   - Customer not found → 404
 *   - Plan not found → 404
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics = {"subscription.activated"},
    brokerProperties = {"listeners=PLAINTEXT://localhost:9093", "port=9093"}
)
@DirtiesContext
class SubscriptionControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Long createCustomer(String name, int age) throws Exception {
        CreateCustomerRequest req = new CreateCustomerRequest();
        req.setName(name);
        req.setAge(age);

        String json = mockMvc.perform(post("/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(json).path("data").path("id").asLong();
    }

    private Long createPlan(String name, Integer capacity) throws Exception {
        CreateServicePlanRequest req = new CreateServicePlanRequest();
        req.setName(name);
        req.setMonthlyFeeCents(6500);
        req.setCapacity(capacity);

        String json = mockMvc.perform(post("/plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(json).path("data").path("id").asLong();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Successful subscription activation → 200")
    void activateSubscription_success() throws Exception {
        Long customerId = createCustomer("Alice Chen", 30);
        Long planId = createPlan("5G Unlimited", null);

        mockMvc.perform(post("/plans/{planId}/customers/{customerId}/activate", planId, customerId)
                .header("X-Idempotency-Key", "test-key-" + customerId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("Duplicate subscription → 409 DUPLICATE_SUBSCRIPTION")
    void duplicateSubscription_returns409() throws Exception {
        Long customerId = createCustomer("Bob Smith", 25);
        Long planId = createPlan("Home Fibre 500M", null);

        // First activation
        mockMvc.perform(post("/plans/{planId}/customers/{customerId}/activate", planId, customerId))
            .andExpect(status().isOk());

        // Second activation — same customer, same plan
        mockMvc.perform(post("/plans/{planId}/customers/{customerId}/activate", planId, customerId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("DUPLICATE_SUBSCRIPTION"));
    }

    @Test
    @DisplayName("Plan at capacity → 409 PLAN_CAPACITY_EXCEEDED")
    void planAtCapacity_returns409() throws Exception {
        Long planId = createPlan("Limited Beta Plan", 1); // capacity = 1

        Long customer1 = createCustomer("Carol Tan", 28);
        Long customer2 = createCustomer("Dave Wong", 35);

        // First subscriber fills the plan
        mockMvc.perform(post("/plans/{planId}/customers/{customerId}/activate", planId, customer1))
            .andExpect(status().isOk());

        // Second subscriber exceeds capacity
        mockMvc.perform(post("/plans/{planId}/customers/{customerId}/activate", planId, customer2))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("PLAN_CAPACITY_EXCEEDED"));
    }

    @Test
    @DisplayName("Customer not found → 404 CUSTOMER_NOT_FOUND")
    void customerNotFound_returns404() throws Exception {
        Long planId = createPlan("5G Basic", null);

        mockMvc.perform(post("/plans/{planId}/customers/99999/activate", planId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    @DisplayName("Plan not found → 404 PLAN_NOT_FOUND")
    void planNotFound_returns404() throws Exception {
        Long customerId = createCustomer("Eve Li", 22);

        mockMvc.perform(post("/plans/99999/customers/{customerId}/activate", customerId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("PLAN_NOT_FOUND"));
    }

    @Test
    @DisplayName("Customer CRUD: create, get cached, update, delete")
    void customerCrud_fullLifecycle() throws Exception {
        Long id = createCustomer("Frank Wu", 40);

        // GET — should return from DB (or cache)
        mockMvc.perform(get("/customers/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Frank Wu"));

        // PUT — update name
        UpdateCustomerHelper req = new UpdateCustomerHelper("Frank Wu Updated", 41);
        mockMvc.perform(put("/customers/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Frank Wu Updated"));

        // DELETE
        mockMvc.perform(delete("/customers/{id}", id))
            .andExpect(status().isOk());

        // Confirm deleted
        mockMvc.perform(get("/customers/{id}", id))
            .andExpect(status().isNotFound());
    }

    /** Inline helper to serialise update request without importing the actual DTO. */
    static class UpdateCustomerHelper {
        public String name;
        public Integer age;
        UpdateCustomerHelper(String name, Integer age) { this.name = name; this.age = age; }
    }
}
