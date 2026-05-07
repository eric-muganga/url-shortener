package com.eric_muganga.url_shortener.config;


import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@Slf4j
public class ResilienceConfig {

    /**
     * Circuit Breaker: If Redis fails, fall back to DB + local cache
     * Thresholds: Open if 5 consecutive failures or > 50% error rate in last 10 calls
     */
    @Bean
    public CircuitBreaker redisCacheCircuitBreaker(CircuitBreakerRegistry circuitBreakerRegistry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // 50% error rate
                .slowCallRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofMillis(500))
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .minimumNumberOfCalls(10)
                .recordException(e -> e instanceof RuntimeException)
                .build();

        return circuitBreakerRegistry.circuitBreaker("redisCacheBreaker", config);
    }

    /**
     * Circuit Breaker Registry with event logging
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        registry.getEventPublisher()
                .onEntryAdded(event -> log.info("Circuit breaker added: {}", event.getAddedEntry().getName()))
                .onEntryRemoved(event -> log.info("Circuit breaker removed: {}", event.getRemovedEntry().getName()));
        return registry;
    }

    /**
     * Retry: If a Redis call fails, retry up to 2 times with 100ms backoff
     */
    @Bean
    public Retry redisRetry(RetryRegistry retryRegistry) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(100))
                .retryOnException(e -> e instanceof RuntimeException)
                .build();

        return retryRegistry.retry("redisRetry", config);
    }

    /**
     * Retry Registry
     */
    @Bean
    public RetryRegistry retryRegistry() {
        return RetryRegistry.ofDefaults();
    }
}
