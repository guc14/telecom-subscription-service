package com.guc.telecom.config;

import com.guc.telecom.event.SubscriptionActivatedEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * KafkaConfig — centralises all Kafka infrastructure bean definitions.
 *
 * Topics:
 *   subscription.activated  — fired after a successful plan activation
 *
 * Producer configuration:
 *   - acks=all + enable.idempotence=true: strongest durability guarantee.
 *     The broker will not acknowledge a write until all ISR replicas confirm it.
 *     Idempotence prevents duplicates on producer retry (network blip scenario).
 *   - retries=3: automatic retry on transient failures.
 *
 * Consumer configuration:
 *   - JsonDeserializer with trusted packages: safe typed deserialisation.
 *   - auto-offset-reset=earliest: new consumer groups start from the beginning
 *     of the topic, useful for local dev and testing.
 */
@Configuration
public class KafkaConfig {

    public static final String TOPIC_SUBSCRIPTION_ACTIVATED = "subscription.activated";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ── Topic Definition ──────────────────────────────────────────────────────

    /**
     * Declares the topic with 3 partitions and replication factor 1 (local dev).
     * In production: replication-factor=3, min.insync.replicas=2.
     */
    @Bean
    public NewTopic subscriptionActivatedTopic() {
        return TopicBuilder.name(TOPIC_SUBSCRIPTION_ACTIVATED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    // ── Producer ─────────────────────────────────────────────────────────────

    @Bean
    public ProducerFactory<String, SubscriptionActivatedEvent> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Strongest durability: wait for all ISR replicas to ack
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        // Retry on transient network errors
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        // Prevent duplicate messages on producer retry
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, SubscriptionActivatedEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ── Consumer ─────────────────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, SubscriptionActivatedEvent> consumerFactory() {
        JsonDeserializer<SubscriptionActivatedEvent> deserializer =
                new JsonDeserializer<>(SubscriptionActivatedEvent.class, false);
        // Only trust our own event package — security best practice
        deserializer.addTrustedPackages("com.guc.telecom.event");

        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "telecom-notification-group");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SubscriptionActivatedEvent>
    kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, SubscriptionActivatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}
