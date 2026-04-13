package com.guc.telecom.controller;

import com.guc.telecom.dto.AdvisorResponse;
import com.guc.telecom.dto.ApiResponse;
import com.guc.telecom.dto.CustomerDto;
import com.guc.telecom.service.AdvisorService;
import com.guc.telecom.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * AdvisorController — exposes the AI plan recommendation endpoint.
 *
 * POST /advisor/customers/{id}?question=...
 *
 * Flow:
 *   1. Fetch customer (verified by CustomerService, Redis-cached)
 *   2. Call AdvisorService → Python sidecar → OpenAI agent loop
 *   3. Agent autonomously queries /plans and /plans/by-customer/{id}
 *   4. Returns personalised plan recommendations
 *
 * If sidecar is unavailable → 503 Service Unavailable.
 * Core subscription endpoints are unaffected.
 */
@RestController
@RequestMapping("/advisor")
@Tag(name = "AI Advisor API", description = "AI-powered service plan recommendations")
public class AdvisorController {

    private final AdvisorService advisorService;
    private final CustomerService customerService;

    public AdvisorController(AdvisorService advisorService, CustomerService customerService) {
        this.advisorService = advisorService;
        this.customerService = customerService;
    }

    @Operation(
        summary = "Get AI plan recommendation for a customer",
        description = "Calls the Python AI sidecar which uses an OpenAI function-calling agent " +
                      "to autonomously query the plan catalogue and return personalised recommendations."
    )
    @PostMapping("/customers/{id}")
    public ApiResponse<AdvisorResponse> getRecommendation(
            @PathVariable Long id,
            @RequestParam(defaultValue = "What service plan would suit me best?") String question) {

        CustomerDto customer = customerService.getCustomerById(id);

        AdvisorResponse response = advisorService.getRecommendation(
            customer.getId(), customer.getName(), customer.getAge(), question);

        if (response == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "AI advisor service is currently unavailable. Please try again later.");
        }

        return ApiResponse.success(response);
    }
}
