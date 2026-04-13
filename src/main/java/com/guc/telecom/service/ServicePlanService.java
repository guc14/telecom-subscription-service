package com.guc.telecom.service;

import com.guc.telecom.dto.*;
import com.guc.telecom.entity.Customer;
import com.guc.telecom.entity.ServicePlan;
import com.guc.telecom.exception.ServicePlanNotFoundException;
import com.guc.telecom.repository.ServicePlanRepository;
import com.guc.telecom.repository.SubscriptionRepository;
import com.guc.telecom.repository.CustomerRepository;
import com.guc.telecom.exception.CustomerNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ServicePlanService {

    private final ServicePlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CustomerRepository customerRepository;
    private final AiDescriptionService aiDescriptionService;

    public ServicePlanService(ServicePlanRepository planRepository,
                               SubscriptionRepository subscriptionRepository,
                               CustomerRepository customerRepository,
                               AiDescriptionService aiDescriptionService) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.customerRepository = customerRepository;
        this.aiDescriptionService = aiDescriptionService;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private ServicePlanDto toDto(ServicePlan plan) {
        ServicePlanDto dto = new ServicePlanDto();
        dto.setId(plan.getId());
        dto.setName(plan.getName());
        dto.setDescription(plan.getDescription());
        dto.setMonthlyFeeCents(plan.getMonthlyFeeCents());
        dto.setCapacity(plan.getCapacity());
        return dto;
    }

    private CustomerDto toCustomerDto(Customer c) {
        return new CustomerDto(c.getId(), c.getName(), c.getAge());
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public List<ServicePlanDto> getAllPlans() {
        return planRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public ServicePlanDto getPlanById(Long id) {
        return toDto(planRepository.findById(id)
                .orElseThrow(() -> new ServicePlanNotFoundException("Plan not found: " + id)));
    }

    /**
     * Creates a service plan.
     * If description is blank, calls OpenAI to auto-generate one.
     * If the AI call fails, the plan is saved without description — non-blocking fallback.
     */
    public ServicePlanDto createPlan(CreateServicePlanRequest request) {
        ServicePlan plan = new ServicePlan();
        plan.setName(request.getName());
        plan.setMonthlyFeeCents(request.getMonthlyFeeCents());
        plan.setCapacity(request.getCapacity());

        String description = request.getDescription();
        if (description == null || description.isBlank()) {
            // Non-blocking: returns null on failure, plan saved without description
            description = aiDescriptionService.generateDescription(
                "telecom service plan: " + request.getName());
        }
        plan.setDescription(description);

        return toDto(planRepository.save(plan));
    }

    public ServicePlanDto updatePlan(Long id, UpdateServicePlanRequest request) {
        ServicePlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ServicePlanNotFoundException("Plan not found: " + id));
        plan.setName(request.getName());
        plan.setDescription(request.getDescription());
        plan.setMonthlyFeeCents(request.getMonthlyFeeCents());
        plan.setCapacity(request.getCapacity());
        return toDto(planRepository.save(plan));
    }

    public void deletePlan(Long id) {
        ServicePlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ServicePlanNotFoundException("Plan not found: " + id));
        planRepository.delete(plan);
    }

    // ── Subscription Queries ─────────────────────────────────────────────────

    public List<ServicePlanDto> getPlansByCustomer(Long customerId) {
        customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + customerId));

        return subscriptionRepository.findByCustomerId(customerId).stream()
                .map(sub -> toDto(sub.getPlan()))
                .collect(Collectors.toList());
    }

    /**
     * DB-level filtered + paginated search for customers on a plan.
     * All filtering pushed to SQL — no in-memory stream.
     */
    public Page<CustomerDto> searchCustomersByPlan(Long planId, String keyword,
                                                    Integer minAge, Integer maxAge,
                                                    Pageable pageable) {
        planRepository.findById(planId)
                .orElseThrow(() -> new ServicePlanNotFoundException("Plan not found: " + planId));

        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        return subscriptionRepository.findCustomersByPlanWithFilters(
                planId, normalizedKeyword, minAge, maxAge, pageable)
                .map(this::toCustomerDto);
    }
}
