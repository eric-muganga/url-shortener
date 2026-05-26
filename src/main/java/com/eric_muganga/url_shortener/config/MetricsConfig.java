package com.eric_muganga.url_shortener.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer metrics configuration.
 * Defines custom metrics and gauge definitions for cache performance monitoring.
 */
@Configuration
public class MetricsConfig {

    /**
     * Register custom metrics with the MeterRegistry.
     * These metrics will be automatically exposed to Prometheus.
     */
    @Bean
    MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags("application", "url-shortener");
    }
}