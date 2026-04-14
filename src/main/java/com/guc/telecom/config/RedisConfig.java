package com.guc.telecom.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * RedisConfig — configures Spring Cache to use Redis with JSON serialisation.
 *
 * Why JSON serialisation instead of Java default:
 *   Default Java serialisation produces binary blobs — impossible to inspect
 *   manually in redis-cli. JSON keys like "customers::42" with human-readable
 *   values are essential for debugging cache issues in production.
 *
 * TTL: 10 minutes for customer cache entries.
 *   Short enough to reflect updates (name/age changes after PUT).
 *   Long enough to absorb repeated reads during high-traffic subscription bursts.
 *
 * Why @ConditionalOnProperty on the @Bean (not the class):
 *   When spring.cache.type=none (test profile), Spring Boot's auto-configuration
 *   provides a no-op CacheManager. But if we manually declare a RedisCacheManager
 *   bean, it overrides that no-op — forcing a real Redis connection even in tests.
 *   Guarding the @Bean with this condition prevents the override, so
 *   spring.cache.type=none actually works as intended in CI/test environments.
 */
@EnableCaching
@Configuration
public class RedisConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            // Human-readable string keys: "customers::42"
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            // JSON values — inspectable in redis-cli
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()))
            // TTL 10 minutes — balances freshness vs DB load
            .entryTtl(Duration.ofMinutes(10))
            // Do not cache null values — prevents caching "customer not found"
            .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .build();
    }
}
