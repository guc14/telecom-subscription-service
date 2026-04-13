package com.guc.telecom.repository;

import com.guc.telecom.entity.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, Long> {
    Optional<CustomerProfile> findByCustomerId(Long customerId);
    boolean existsByCustomerId(Long customerId);
}
