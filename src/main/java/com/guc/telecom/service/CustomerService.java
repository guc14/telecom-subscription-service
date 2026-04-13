package com.guc.telecom.service;

import com.guc.telecom.dto.*;
import com.guc.telecom.entity.Customer;
import com.guc.telecom.entity.CustomerProfile;
import com.guc.telecom.exception.*;
import com.guc.telecom.repository.CustomerProfileRepository;
import com.guc.telecom.repository.CustomerRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CustomerService — manages telecom customer lifecycle.
 *
 * Caching strategy:
 *   GET /customers/{id} is cached in Redis (key: "customers::{id}", TTL 10 min).
 *   Every subscription activation first fetches the customer by ID to verify existence
 *   and get the name for the Kafka event — caching this read significantly reduces
 *   DB load under high subscription throughput.
 *   Cache is evicted on PUT and DELETE to prevent stale reads.
 */
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerProfileRepository profileRepository;

    public CustomerService(CustomerRepository customerRepository,
                            CustomerProfileRepository profileRepository) {
        this.customerRepository = customerRepository;
        this.profileRepository = profileRepository;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private CustomerDto toDto(Customer c) {
        return new CustomerDto(c.getId(), c.getName(), c.getAge());
    }

    private CustomerProfileDto toProfileDto(CustomerProfile p) {
        CustomerProfileDto dto = new CustomerProfileDto();
        dto.setId(p.getId());
        dto.setCustomerId(p.getCustomer().getId());
        dto.setBillingAddress(p.getBillingAddress());
        dto.setAccountTier(p.getAccountTier());
        dto.setPreferredContact(p.getPreferredContact());
        return dto;
    }

    // ── Customer CRUD ─────────────────────────────────────────────────────────

    public List<CustomerDto> getAllCustomers() {
        return customerRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public Page<CustomerDto> getCustomersPage(Pageable pageable) {
        return customerRepository.findAll(pageable).map(this::toDto);
    }

    /**
     * Cached by Redis — key: "customers::{id}", TTL 10 min.
     * Hot path: called on every subscription activation to verify customer exists.
     */
    @Cacheable(value = "customers", key = "#id")
    public CustomerDto getCustomerById(Long id) {
        return toDto(customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + id)));
    }

    public CustomerDto addCustomer(CreateCustomerRequest request) {
        Customer c = new Customer();
        c.setName(request.getName());
        c.setAge(request.getAge());
        return toDto(customerRepository.save(c));
    }

    @CacheEvict(value = "customers", key = "#id")
    public CustomerDto updateCustomer(Long id, UpdateCustomerRequest request) {
        Customer c = customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + id));
        c.setName(request.getName());
        c.setAge(request.getAge());
        return toDto(customerRepository.save(c));
    }

    @CacheEvict(value = "customers", key = "#id")
    public void deleteCustomer(Long id) {
        Customer c = customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + id));
        customerRepository.delete(c);
    }

    public Page<CustomerDto> searchCustomers(String keyword, Integer minAge,
                                              Integer maxAge, Pageable pageable) {
        String trimmed = (keyword == null) ? null : keyword.trim();
        boolean hasKeyword = trimmed != null && !trimmed.isEmpty();
        boolean hasAge = minAge != null && maxAge != null;

        if (hasAge && minAge > maxAge) { int t = minAge; minAge = maxAge; maxAge = t; }

        Page<Customer> page;
        if (hasKeyword && hasAge)
            page = customerRepository.findByNameContainingIgnoreCaseAndAgeBetween(trimmed, minAge, maxAge, pageable);
        else if (hasKeyword)
            page = customerRepository.findByNameContainingIgnoreCase(trimmed, pageable);
        else if (hasAge)
            page = customerRepository.findByAgeBetween(minAge, maxAge, pageable);
        else
            page = customerRepository.findAll(pageable);

        return page.map(this::toDto);
    }

    // ── Customer Profile CRUD ─────────────────────────────────────────────────

    public CustomerProfileDto getProfile(Long customerId) {
        CustomerProfile p = profileRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Profile not found for customer: " + customerId));
        return toProfileDto(p);
    }

    public CustomerProfileDto createProfile(Long customerId, CreateCustomerProfileRequest request) {
        customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + customerId));

        if (profileRepository.existsByCustomerId(customerId))
            throw new DuplicateProfileException("Profile already exists for customer: " + customerId);

        Customer customer = customerRepository.getReferenceById(customerId);
        CustomerProfile profile = new CustomerProfile();
        profile.setCustomer(customer);
        profile.setBillingAddress(request.getBillingAddress());
        profile.setAccountTier(request.getAccountTier());
        profile.setPreferredContact(request.getPreferredContact());
        return toProfileDto(profileRepository.save(profile));
    }

    public CustomerProfileDto updateProfile(Long customerId, CreateCustomerProfileRequest request) {
        CustomerProfile profile = profileRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Profile not found for customer: " + customerId));
        profile.setBillingAddress(request.getBillingAddress());
        profile.setAccountTier(request.getAccountTier());
        profile.setPreferredContact(request.getPreferredContact());
        return toProfileDto(profileRepository.save(profile));
    }

    public void deleteProfile(Long customerId) {
        CustomerProfile profile = profileRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Profile not found for customer: " + customerId));
        profileRepository.delete(profile);
    }
}
