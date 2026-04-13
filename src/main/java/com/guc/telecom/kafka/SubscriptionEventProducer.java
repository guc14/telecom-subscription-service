package com.guc.telecom.kafka;

import com.guc.telecom.config.KafkaConfig;
import com.guc.telecom.event.SubscriptionActivatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes subscription domain events to Kafka.
 *
 * Events are sent asynchronously using the customer ID as the partition key,
 * so all events for a given customer land on the same partition and are
 * consumed in order. Kafka send failures are logged but do not affect the
 * HTTP response — the subscription is already committed to the database.
 */
@Component
public class SubscriptionEventProducer {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionEventProducer.class);

    private final KafkaTemplate<String, SubscriptionActivatedEvent> kafkaTemplate;

    public SubscriptionEventProducer(KafkaTemplate<String, SubscriptionActivatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes a SubscriptionActivatedEvent to the `subscription.activated` topic.
     *
     * @param event the activation event built by SubscriptionService
     */
    public void publishSubscriptionActivated(SubscriptionActivatedEvent event) {
        // Partition key: customerId → ordered events per customer
        String partitionKey = event.getCustomerId().toString();

        CompletableFuture<SendResult<String, SubscriptionActivatedEvent>> future =
                kafkaTemplate.send(KafkaConfig.TOPIC_SUBSCRIPTION_ACTIVATED, partitionKey, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                // Log failure — do NOT rethrow. Subscription is already saved.
                logger.error(
                    "Failed to publish SubscriptionActivatedEvent for customerId={}, planId={}: {}",
                    event.getCustomerId(), event.getPlanId(), ex.getMessage()
                );
            } else {
                logger.info(
                    "Published SubscriptionActivatedEvent: customerId={}, planId={}, " +
                    "topic={}, partition={}, offset={}",
                    event.getCustomerId(),
                    event.getPlanId(),
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
                );
            }
        });
    }
}
