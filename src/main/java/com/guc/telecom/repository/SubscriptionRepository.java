package com.guc.telecom.repository;

import com.guc.telecom.entity.Customer;
import com.guc.telecom.entity.Subscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    boolean existsByCustomerIdAndPlanId(Long customerId, Long planId);

    long countByPlanId(Long planId);

    List<Subscription> findByCustomerId(Long customerId);

    List<Subscription> findByPlanId(Long planId);

    /**
     * DB-level paginated search: find customers on a plan, with optional
     * name keyword and age range filters — all pushed to SQL.
     *
     * The (:keyword IS NULL OR ...) pattern handles the "no filter" case
     * without requiring separate repository methods per combination.
     *
     * Design note: filtering in DB (not in-memory stream) is critical for
     * plans with thousands of subscribers — loading all rows into JVM heap
     * just to filter page 1 is a common performance anti-pattern.
     */
    @Query("""
        SELECT s.customer FROM Subscription s
        WHERE s.plan.id = :planId
          AND (:keyword IS NULL OR LOWER(s.customer.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (:minAge IS NULL OR s.customer.age >= :minAge)
          AND (:maxAge IS NULL OR s.customer.age <= :maxAge)
        """)
    Page<Customer> findCustomersByPlanWithFilters(
            @Param("planId") Long planId,
            @Param("keyword") String keyword,
            @Param("minAge") Integer minAge,
            @Param("maxAge") Integer maxAge,
            Pageable pageable);
}
