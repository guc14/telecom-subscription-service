package com.guc.telecom.kafka;

import com.guc.telecom.config.KafkaConfig;
import com.guc.telecom.event.SubscriptionActivatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for subscription activation events.
 *
 * Responsibilities:
 * - Trigger customer notification workflow
 * - Simulate downstream CRM / billing integration
 * - Consume events asynchronously from Kafka
 *
 * Consumer Group:
 * telecom-notification-group
 */
@Component
public class SubscriptionNotificationConsumer {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionNotificationConsumer.class);

    /**
     * Listens to the subscription.activated topic.
     *
     * @param event         deserialised event payload
     * @param partition     Kafka partition (logged for observability)
     * @param offset        Kafka offset (logged for observability)
     */
    @KafkaListener(
        topics = KafkaConfig.TOPIC_SUBSCRIPTION_ACTIVATED,
        groupId = "telecom-notification-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleSubscriptionActivated(
            @Payload SubscriptionActivatedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        logger.info(
            "[Kafka Consumer] Received SubscriptionActivatedEvent: " +
            "partition={}, offset={}, customerId={}, customerName={}, planName={}, activatedAt={}",
            partition, offset,
            event.getCustomerId(),
            event.getCustomerName(),
            event.getPlanName(),
            event.getActivatedAt()
        );

        try {
            sendWelcomeNotification(event);
            updateCrmSystem(event);
            writeAuditLog(event);
        } catch (Exception ex) {
            // Log and swallow — do not rethrow, which would cause Kafka to retry
            // In production: send to a Dead Letter Topic (DLT) instead
            logger.error(
                "[Kafka Consumer] Failed to process event for customerId={}: {}",
                event.getCustomerId(), ex.getMessage(), ex
            );
        }
    }

    /**
     * Simulates sending a welcome notification via SMS/email.
     * In production: inject a NotificationGateway and call the real API.
     */
    private void sendWelcomeNotification(SubscriptionActivatedEvent event) {
        logger.info(
            "[Notification] Sending welcome message to customer '{}' (id={}) for plan '{}'",
            event.getCustomerName(), event.getCustomerId(), event.getPlanName()
        );
        // Real impl: smsGateway.send(customer.getPhone(), buildWelcomeMessage(event));
    }

    /**
     * Simulates updating the CRM system with the new active plan.
     */
    private void updateCrmSystem(SubscriptionActivatedEvent event) {
        logger.info(
            "[CRM] Updating customer {} active plan to '{}' in CRM",
            event.getCustomerId(), event.getPlanName()
        );
        // Real impl: crmClient.updateActivePlan(event.getCustomerId(), event.getPlanId());
    }

    /**
     * Simulates writing an immutable audit log entry.
     */
    private void writeAuditLog(SubscriptionActivatedEvent event) {
        logger.info(
            "[Audit] SUBSCRIPTION_ACTIVATED | customerId={} | planId={} | at={} | idempotencyKey={}",
            event.getCustomerId(), event.getPlanId(),
            event.getActivatedAt(), event.getIdempotencyKey()
        );
    }
}
