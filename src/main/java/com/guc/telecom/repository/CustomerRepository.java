package com.guc.telecom.repository;

import com.guc.telecom.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Page<Customer> findByNameContainingIgnoreCase(String keyword, Pageable pageable);
    Page<Customer> findByAgeBetween(Integer minAge, Integer maxAge, Pageable pageable);
    Page<Customer> findByNameContainingIgnoreCaseAndAgeBetween(
            String keyword, Integer minAge, Integer maxAge, Pageable pageable);
}
