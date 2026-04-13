package com.guc.telecom.repository;

import com.guc.telecom.entity.ServicePlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServicePlanRepository extends JpaRepository<ServicePlan, Long> {
}
