package com.guc.telecom.service;

import com.guc.telecom.dto.SubscriptionInfoDto;
import com.guc.telecom.entity.Customer;
import com.guc.telecom.entity.ServicePlan;
import com.guc.telecom.entity.Subscription;
import com.guc.telecom.event.SubscriptionActivatedEvent;
import com.guc.telecom.exception.*;
import com.guc.telecom.kafka.SubscriptionEventProducer;
import com.guc.telecom.repository.CustomerRepository;
import com.guc.telecom.repository.ServicePlanRepository;
import com.guc.telecom.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Handles subscription activation for customers on service plans.
 *
 * Idempotency is implemented at two levels: a Redis key check on the HTTP
 * layer (fast path, 24h TTL) and a unique constraint on (customer_id, plan_id)
 * in the database as a fallback. This ensures safe retries on network failures
 * without creating duplicate subscriptions.
 *
 * After a successful activation, a SubscriptionActivatedEvent is published
 * to Kafka asynchronously. Kafka unavailability does not roll back the
 * subscription — the DB write is the source of truth.
 */
@Service
public class SubscriptionService {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

    // Redis key prefix for idempotency tokens
    private static final String IDEMPOTENCY_PREFIX = "idempotency:subscription:";
    // Idempotency key TTL — 24 hours is standard for payment/telecom APIs
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    private final SubscriptionRepository subscriptionRepository;
    private final CustomerRepository customerRepository;
    private final ServicePlanRepository servicePlanRepository;
    private final StringRedisTemplate redisTemplate;
    private final SubscriptionEventProducer eventProducer;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                                CustomerRepository customerRepository,
                                ServicePlanRepository servicePlanRepository,
                                StringRedisTemplate redisTemplate,
                                SubscriptionEventProducer eventProducer) {
        this.subscriptionRepository = subscriptionRepository;
        this.customerRepository = customerRepository;
        this.servicePlanRepository = servicePlanRepository;
        this.redisTemplate = redisTemplate;
        this.eventProducer = eventProducer;
    }

    /**
     * Activates a subscription for a customer on a service plan.
     *
     * Idempotent: calling with the same idempotency key multiple times
     * is safe — only the first call creates a subscription.
     *
     * @param customerId      the customer requesting the plan
     * @param planId          the service plan to subscribe to
     * @param idempotencyKey  client-supplied deduplication key (from X-Idempotency-Key header)
     * @throws DuplicateSubscriptionException   if customer is already on this plan
     * @throws PlanCapacityExceededException    if the plan has no remaining capacity
     * @throws CustomerNotFoundException        if customerId does not exist
     * @throws ServicePlanNotFoundException     if planId does not exist
     */
    @Transactional
    public void activateSubscription(Long customerId, Long planId, String idempotencyKey) {

        // ── Step 1: Idempotency check (Redis fast path) ───────────────────────
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
            Boolean alreadyProcessed = redisTemplate.hasKey(redisKey);
            if (Boolean.TRUE.equals(alreadyProcessed)) {
                logger.info(
                    "Idempotency hit: key={} already processed. Skipping activation for customerId={}, planId={}",
                    idempotencyKey, customerId, planId
                );
                return; // Safe no-op — client gets 200, no duplicate created
            }
        }

        // ── Step 2: Validate customer ────────────────────────────────────────
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(
                    "Customer not found with id = " + customerId));

        // ── Step 3: Validate plan ────────────────────────────────────────────
        ServicePlan plan = servicePlanRepository.findById(planId)
                .orElseThrow(() -> new ServicePlanNotFoundException(
                    "Service plan not found with id = " + planId));

        // ── Step 4: Prevent duplicate subscription ───────────────────────────
        if (subscriptionRepository.existsByCustomerIdAndPlanId(customerId, planId)) {
            throw new DuplicateSubscriptionException(
                "Customer " + customerId + " is already subscribed to plan " + planId);
        }

        // ── Step 5: Enforce plan capacity ────────────────────────────────────
        if (plan.getCapacity() != null) {
            long currentCount = subscriptionRepository.countByPlanId(planId);
            if (currentCount >= plan.getCapacity()) {
                throw new PlanCapacityExceededException(
                    "Plan " + planId + " has reached its capacity of " + plan.getCapacity());
            }
        }

        // ── Step 6: Persist subscription ─────────────────────────────────────
        Subscription subscription = new Subscription();
        subscription.setCustomer(customer);
        subscription.setPlan(plan);
        subscription.setActivatedAt(LocalDateTime.now());
        subscription.setStatus("ACTIVE");
        subscription.setIdempotencyKey(idempotencyKey);
        Subscription saved = subscriptionRepository.save(subscription);

        // ── Step 7: Store idempotency key in Redis (24h TTL) ─────────────────
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
            redisTemplate.opsForValue().set(redisKey, "ACTIVATED", IDEMPOTENCY_TTL_HOURS, TimeUnit.HOURS);
            logger.info("Stored idempotency key in Redis: {}", redisKey);
        }

        // ── Step 8: Publish Kafka event (async, non-blocking) ─────────────────
        SubscriptionActivatedEvent event = new SubscriptionActivatedEvent(
            saved.getId(),
            customer.getId(),
            customer.getName(),
            plan.getId(),
            plan.getName(),
            saved.getStatus(),
            saved.getActivatedAt(),
            idempotencyKey
        );
        eventProducer.publishSubscriptionActivated(event);

        logger.info("Subscription activated: customerId={}, planId={}, subscriptionId={}",
            customerId, planId, saved.getId());
    }

    /**
     * Returns all active subscriptions for a customer.
     */
    public List<SubscriptionInfoDto> getSubscriptionsByCustomer(Long customerId) {
        customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(
                    "Customer not found with id = " + customerId));

        return subscriptionRepository.findByCustomerId(customerId).stream()
                .map(sub -> new SubscriptionInfoDto(
                    sub.getId(),
                    sub.getCustomer().getId(),
                    sub.getCustomer().getName(),
                    sub.getPlan().getId(),
                    sub.getPlan().getName(),
                    sub.getActivatedAt(),
                    sub.getStatus()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Returns all customers subscribed to a given plan.
     */
    public List<SubscriptionInfoDto> getSubscriptionsByPlan(Long planId) {
        servicePlanRepository.findById(planId)
                .orElseThrow(() -> new ServicePlanNotFoundException(
                    "Service plan not found with id = " + planId));

        return subscriptionRepository.findByPlanId(planId).stream()
                .map(sub -> new SubscriptionInfoDto(
                    sub.getId(),
                    sub.getCustomer().getId(),
                    sub.getCustomer().getName(),
                    sub.getPlan().getId(),
                    sub.getPlan().getName(),
                    sub.getActivatedAt(),
                    sub.getStatus()
                ))
                .collect(Collectors.toList());
    }
}
