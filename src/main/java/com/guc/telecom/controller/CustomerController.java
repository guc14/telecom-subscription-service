package com.guc.telecom.controller;

import com.guc.telecom.dto.*;
import com.guc.telecom.service.CustomerService;
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
 * CustomerController — REST endpoints for telecom customer management.
 *
 * Base path: /customers
 *
 * GET /customers/{id} is Redis-cached (TTL 10 min).
 * This is the hot path — every subscription activation verifies the customer
 * by ID before proceeding. Caching reduces DB load under high activation throughput.
 */
@RestController
@RequestMapping("/customers")
@Tag(name = "Customer API", description = "Telecom customer management and profile operations")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    // ── Customer CRUD ─────────────────────────────────────────────────────────

    @Operation(summary = "Get all customers")
    @GetMapping
    public ApiResponse<List<CustomerDto>> getAllCustomers() {
        return ApiResponse.success(customerService.getAllCustomers());
    }

    @Operation(summary = "Get paginated customer list")
    @GetMapping("/page")
    public ApiResponse<Page<CustomerDto>> getCustomersPage(
            @PageableDefault(page = 0, size = 10, sort = "id") Pageable pageable) {
        return ApiResponse.success(customerService.getCustomersPage(pageable));
    }

    @Operation(summary = "Get customer by ID (Redis cached, TTL 10 min)")
    @GetMapping("/{id}")
    public ApiResponse<CustomerDto> getCustomerById(
            @Parameter(description = "Customer ID") @PathVariable Long id) {
        return ApiResponse.success(customerService.getCustomerById(id));
    }

    @Operation(summary = "Create a new customer")
    @PostMapping
    public ApiResponse<CustomerDto> addCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        return ApiResponse.success(customerService.addCustomer(request));
    }

    @Operation(summary = "Update a customer (evicts cache)")
    @PutMapping("/{id}")
    public ApiResponse<CustomerDto> updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCustomerRequest request) {
        return ApiResponse.success(customerService.updateCustomer(id, request));
    }

    @Operation(summary = "Delete a customer (evicts cache)")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return ApiResponse.success(null);
    }

    @Operation(
        summary = "Search customers",
        description = "Filter by name keyword and age range with pagination. " +
                      "All filtering is DB-level — no in-memory stream."
    )
    @GetMapping("/search")
    public ApiResponse<Page<CustomerDto>> searchCustomers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer minAge,
            @RequestParam(required = false) Integer maxAge,
            @PageableDefault(page = 0, size = 10, sort = "id") Pageable pageable) {
        return ApiResponse.success(customerService.searchCustomers(keyword, minAge, maxAge, pageable));
    }

    // ── Customer Profile ──────────────────────────────────────────────────────

    @Operation(summary = "Get customer billing profile")
    @GetMapping("/{id}/profile")
    public ApiResponse<CustomerProfileDto> getProfile(@PathVariable Long id) {
        return ApiResponse.success(customerService.getProfile(id));
    }

    @Operation(summary = "Create customer billing profile")
    @PostMapping("/{id}/profile")
    public ApiResponse<CustomerProfileDto> createProfile(
            @PathVariable Long id,
            @Valid @RequestBody CreateCustomerProfileRequest request) {
        return ApiResponse.success(customerService.createProfile(id, request));
    }

    @Operation(summary = "Update customer billing profile")
    @PutMapping("/{id}/profile")
    public ApiResponse<CustomerProfileDto> updateProfile(
            @PathVariable Long id,
            @Valid @RequestBody CreateCustomerProfileRequest request) {
        return ApiResponse.success(customerService.updateProfile(id, request));
    }

    @Operation(summary = "Delete customer billing profile")
    @DeleteMapping("/{id}/profile")
    public ApiResponse<Void> deleteProfile(@PathVariable Long id) {
        customerService.deleteProfile(id);
        return ApiResponse.success(null);
    }
}
