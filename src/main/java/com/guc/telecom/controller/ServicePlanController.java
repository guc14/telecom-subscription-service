package com.guc.telecom.controller;

import com.guc.telecom.dto.*;
import com.guc.telecom.service.ServicePlanService;
import com.guc.telecom.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ServicePlanController — REST endpoints for service plan management
 * and customer subscription operations.
 *
 * Base path: /plans
 *
 * Idempotency:
 *   POST /plans/{planId}/customers/{customerId}/activate accepts an optional
 *   X-Idempotency-Key header. If provided, duplicate requests with the same key
 *   within 24 hours are safely ignored — the customer is not double-subscribed.
 *   This is mandatory for any activation endpoint in telecom/payment systems
 *   where network retries are common.
 */
@RestController
@RequestMapping("/plans")
@Tag(name = "Service Plan API", description = "Service plan management and customer subscription operations")
public class ServicePlanController {

    private final ServicePlanService planService;
    private final SubscriptionService subscriptionService;

    public ServicePlanController(ServicePlanService planService,
                                  SubscriptionService subscriptionService) {
        this.planService = planService;
        this.subscriptionService = subscriptionService;
    }

    // ───────────── Service Plan CRUD ─────────────────────────────────────────

    @Operation(summary = "Get all service plans")
    @GetMapping
    public ApiResponse<List<ServicePlanDto>> getAllPlans() {
        return ApiResponse.success(planService.getAllPlans());
    }

    @Operation(summary = "Get service plan by ID")
    @GetMapping("/{id}")
    public ApiResponse<ServicePlanDto> getPlanById(@PathVariable Long id) {
        return ApiResponse.success(planService.getPlanById(id));
    }

    @Operation(
        summary = "Create a new service plan",
        description = "If description is omitted, an AI-generated description is created automatically."
    )
    @PostMapping
    public ApiResponse<ServicePlanDto> createPlan(@Valid @RequestBody CreateServicePlanRequest request) {
        return ApiResponse.success(planService.createPlan(request));
    }

    @Operation(summary = "Update a service plan")
    @PutMapping("/{id}")
    public ApiResponse<ServicePlanDto> updatePlan(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateServicePlanRequest request) {
        return ApiResponse.success(planService.updatePlan(id, request));
    }

    @Operation(summary = "Delete a service plan")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePlan(@PathVariable Long id) {
        planService.deletePlan(id);
        return ApiResponse.success(null);
    }

    // ───────────── Subscription Operations ───────────────────────────────────

    /**
     * Activate a subscription — idempotent endpoint.
     *
     * X-Idempotency-Key header (optional but strongly recommended):
     *   If provided, duplicate calls within 24h with the same key are safely ignored.
     *   Use a UUID generated client-side: X-Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
     *
     * Business rules enforced (in order):
     *   1. Customer must exist → 404
     *   2. Plan must exist → 404
     *   3. Customer must not already be subscribed → 409 DUPLICATE_SUBSCRIPTION
     *   4. Plan must have remaining capacity (if capped) → 409 PLAN_CAPACITY_EXCEEDED
     *   5. Kafka event published after successful activation
     */
    @Operation(
        summary = "Activate a subscription (idempotent)",
        description = "Subscribe a customer to a service plan. " +
                      "Supply X-Idempotency-Key header to safely retry on network failures."
    )
    @PostMapping("/{planId}/customers/{customerId}/activate")
    public ApiResponse<Void> activateSubscription(
            @Parameter(description = "Service Plan ID") @PathVariable Long planId,
            @Parameter(description = "Customer ID") @PathVariable Long customerId,
            @Parameter(description = "Optional idempotency key to prevent duplicate activations")
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey
    ) {
        subscriptionService.activateSubscription(customerId, planId, idempotencyKey);
        return ApiResponse.success(null);
    }

    @Operation(summary = "Get all customers subscribed to a plan")
    @GetMapping("/{planId}/customers")
    public ApiResponse<List<SubscriptionInfoDto>> getCustomersByPlan(@PathVariable Long planId) {
        return ApiResponse.success(subscriptionService.getSubscriptionsByPlan(planId));
    }

    @Operation(
        summary = "Search customers on a plan",
        description = "Filter by name keyword and age range, with pagination. DB-level query — does not load all records into memory."
    )
    @GetMapping("/{planId}/customers/search")
    public ApiResponse<Page<CustomerDto>> searchCustomersOnPlan(
            @PathVariable Long planId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer minAge,
            @RequestParam(required = false) Integer maxAge,
            @PageableDefault(page = 0, size = 10, sort = "id") Pageable pageable
    ) {
        return ApiResponse.success(planService.searchCustomersByPlan(planId, keyword, minAge, maxAge, pageable));
    }

    @Operation(summary = "Get all plans a customer is subscribed to")
    @GetMapping("/by-customer/{customerId}")
    public ApiResponse<List<ServicePlanDto>> getPlansByCustomer(@PathVariable Long customerId) {
        return ApiResponse.success(planService.getPlansByCustomer(customerId));
    }

    @Operation(summary = "Get full subscription history for a customer")
    @GetMapping("/subscriptions/by-customer/{customerId}")
    public ApiResponse<List<SubscriptionInfoDto>> getSubscriptionsByCustomer(@PathVariable Long customerId) {
        return ApiResponse.success(subscriptionService.getSubscriptionsByCustomer(customerId));
    }
}
