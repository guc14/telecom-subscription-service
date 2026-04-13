package com.guc.telecom.service;

import com.guc.telecom.entity.Customer;
import com.guc.telecom.entity.ServicePlan;
import com.guc.telecom.entity.Subscription;
import com.guc.telecom.exception.*;
import com.guc.telecom.kafka.SubscriptionEventProducer;
import com.guc.telecom.repository.CustomerRepository;
import com.guc.telecom.repository.ServicePlanRepository;
import com.guc.telecom.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SubscriptionService.
 *
 * Uses Mockito — no Spring context, no DB, no Redis, no Kafka.
 * Fast feedback loop: all mocks, tests run in milliseconds.
 *
 * Coverage:
 *   - Idempotency: duplicate X-Idempotency-Key skips activation
 *   - Customer not found → CustomerNotFoundException
 *   - Plan not found → ServicePlanNotFoundException
 *   - Duplicate subscription → DuplicateSubscriptionException
 *   - Plan at capacity → PlanCapacityExceededException
 *   - Happy path: subscription saved, Redis key stored, Kafka event published
 *   - Null idempotency key: activation proceeds without Redis check
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private ServicePlanRepository servicePlanRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private SubscriptionEventProducer eventProducer;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private Customer customer;
    private ServicePlan plan;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setId(1L);
        customer.setName("Alice Chen");
        customer.setAge(30);

        plan = new ServicePlan();
        plan.setId(10L);
        plan.setName("5G Unlimited Plus");
        plan.setCapacity(null); // unlimited by default
    }

    // ── Idempotency ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Duplicate idempotency key → skip activation, no DB write")
    void idempotencyKey_alreadyProcessed_skipsActivation() {
        String key = "idem-key-001";
        when(redisTemplate.hasKey("idempotency:subscription:" + key)).thenReturn(true);

        subscriptionService.activateSubscription(1L, 10L, key);

        // No DB access should happen at all
        verify(customerRepository, never()).findById(any());
        verify(subscriptionRepository, never()).save(any());
        verify(eventProducer, never()).publishSubscriptionActivated(any());
    }

    @Test
    @DisplayName("Null idempotency key → no Redis check, activation proceeds normally")
    void nullIdempotencyKey_noRedisCheck_activationProceeds() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(servicePlanRepository.findById(10L)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.existsByCustomerIdAndPlanId(1L, 10L)).thenReturn(false);

        Subscription saved = new Subscription();
        saved.setId(100L);
        saved.setCustomer(customer);
        saved.setPlan(plan);
        when(subscriptionRepository.save(any())).thenReturn(saved);

        subscriptionService.activateSubscription(1L, 10L, null);

        // Redis should not be consulted when key is null
        verify(redisTemplate, never()).hasKey(any());
        verify(subscriptionRepository).save(any());
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Customer not found → CustomerNotFoundException")
    void customerNotFound_throwsException() {
        when(redisTemplate.hasKey(any())).thenReturn(false);
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.activateSubscription(99L, 10L, "key-x"))
            .isInstanceOf(CustomerNotFoundException.class)
            .hasMessageContaining("99");
    }

    @Test
    @DisplayName("Plan not found → ServicePlanNotFoundException")
    void planNotFound_throwsException() {
        when(redisTemplate.hasKey(any())).thenReturn(false);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(servicePlanRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.activateSubscription(1L, 99L, "key-x"))
            .isInstanceOf(ServicePlanNotFoundException.class)
            .hasMessageContaining("99");
    }

    @Test
    @DisplayName("Already subscribed → DuplicateSubscriptionException")
    void duplicateSubscription_throwsException() {
        when(redisTemplate.hasKey(any())).thenReturn(false);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(servicePlanRepository.findById(10L)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.existsByCustomerIdAndPlanId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> subscriptionService.activateSubscription(1L, 10L, "key-x"))
            .isInstanceOf(DuplicateSubscriptionException.class)
            .hasMessageContaining("already subscribed");
    }

    @Test
    @DisplayName("Plan at capacity → PlanCapacityExceededException")
    void planAtCapacity_throwsException() {
        plan.setCapacity(5);
        when(redisTemplate.hasKey(any())).thenReturn(false);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(servicePlanRepository.findById(10L)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.existsByCustomerIdAndPlanId(1L, 10L)).thenReturn(false);
        when(subscriptionRepository.countByPlanId(10L)).thenReturn(5L); // at cap

        assertThatThrownBy(() -> subscriptionService.activateSubscription(1L, 10L, "key-x"))
            .isInstanceOf(PlanCapacityExceededException.class)
            .hasMessageContaining("capacity");
    }

    @Test
    @DisplayName("Plan below capacity → activation succeeds")
    void planBelowCapacity_activationSucceeds() {
        plan.setCapacity(10);
        String idempotencyKey = "key-happy";

        when(redisTemplate.hasKey(any())).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(servicePlanRepository.findById(10L)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.existsByCustomerIdAndPlanId(1L, 10L)).thenReturn(false);
        when(subscriptionRepository.countByPlanId(10L)).thenReturn(3L); // below cap

        Subscription saved = new Subscription();
        saved.setId(100L);
        saved.setCustomer(customer);
        saved.setPlan(plan);
        when(subscriptionRepository.save(any())).thenReturn(saved);

        subscriptionService.activateSubscription(1L, 10L, idempotencyKey);

        verify(subscriptionRepository).save(any());
        verify(valueOps).set(eq("idempotency:subscription:" + idempotencyKey), eq("ACTIVATED"), anyLong(), any());
        verify(eventProducer).publishSubscriptionActivated(any());
    }

    // ── Happy Path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Happy path: subscription saved, Redis key stored, Kafka event published")
    void happyPath_allStepsExecuted() {
        String idempotencyKey = "uuid-1234";

        when(redisTemplate.hasKey(any())).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(servicePlanRepository.findById(10L)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.existsByCustomerIdAndPlanId(1L, 10L)).thenReturn(false);

        ArgumentCaptor<Subscription> subCaptor = ArgumentCaptor.forClass(Subscription.class);
        Subscription saved = new Subscription();
        saved.setId(55L);
        saved.setCustomer(customer);
        saved.setPlan(plan);
        saved.setStatus("ACTIVE");
        when(subscriptionRepository.save(subCaptor.capture())).thenReturn(saved);

        subscriptionService.activateSubscription(1L, 10L, idempotencyKey);

        // Verify subscription entity fields
        Subscription captured = subCaptor.getValue();
        assertThat(captured.getCustomer()).isEqualTo(customer);
        assertThat(captured.getPlan()).isEqualTo(plan);
        assertThat(captured.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(captured.getStatus()).isEqualTo("ACTIVE");

        // Verify Redis idempotency key stored
        verify(valueOps).set(
            eq("idempotency:subscription:" + idempotencyKey),
            eq("ACTIVATED"),
            anyLong(), any()
        );

        // Verify Kafka event published
        verify(eventProducer).publishSubscriptionActivated(argThat(event ->
            event.getCustomerId().equals(1L) &&
            event.getPlanName().equals("5G Unlimited Plus") &&
            event.getIdempotencyKey().equals(idempotencyKey)
        ));
    }
}
