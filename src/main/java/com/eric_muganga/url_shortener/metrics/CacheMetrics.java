package com.eric_muganga.url_shortener.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class CacheMetrics {
    private final MeterRegistry meterRegistry;

    // Counters for cache hits
    private final AtomicLong cacheHitsCaffeine = new AtomicLong(0);
    private final AtomicLong cacheHitsRedis = new AtomicLong(0);
    private final AtomicLong cacheHitsDatabase = new AtomicLong(0);
    private final AtomicLong totalRequests = new AtomicLong(0);

    // Counters for failures and anomalies
    private final AtomicLong collisionsDetected = new AtomicLong(0);
    private final AtomicLong circuitBreakerOpenCount = new AtomicLong(0);
    private final AtomicLong redisWriteFailures = new AtomicLong(0);

    // Counters for operations
    private final Counter urlCreatedCounter;
    private final Counter urlUpdatedCounter;
    private final Counter urlDeletedCounter;
    private final Counter cacheInvalidationCounter;

    // Timers for latency measurement
    private final Timer caffeineCacheAccessTimer;
    private final Timer redisCacheAccessTimer;
    private final Timer databaseAccessTimer;

    public CacheMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Register gauges for cache hit counts
        Gauge.builder("cache.hits.caffeine", cacheHitsCaffeine::get)
                .description("Total cache hits from Caffeine L1 cache")
                .register(meterRegistry);

        Gauge.builder("cache.hits.redis", cacheHitsRedis::get)
                .description("Total cache hits from Redis L2 cache")
                .register(meterRegistry);

        Gauge.builder("cache.hits.database", cacheHitsDatabase::get)
                .description("Total cache hits from database L3")
                .register(meterRegistry);

        Gauge.builder("cache.requests.total", totalRequests::get)
                .description("Total number of URL lookup requests")
                .register(meterRegistry);

        // Register gauge for anomalies
        Gauge.builder("url.collisions", collisionsDetected::get)
                .description("Total short code collisions detected")
                .register(meterRegistry);

        Gauge.builder("circuit.breaker.open.count", circuitBreakerOpenCount::get)
                .description("Number of times Redis circuit breaker entered OPEN state")
                .register(meterRegistry);

        Gauge.builder("redis.write.failures", redisWriteFailures::get)
                .description("Total failures to write to Redis cache")
                .register(meterRegistry);

        // Register counters for operations
        this.urlCreatedCounter = Counter.builder("url.created")
                .description("Total short URLs created")
                .register(meterRegistry);

        this.urlUpdatedCounter = Counter.builder("url.updated")
                .description("Total short URLs updated")
                .register(meterRegistry);

        this.urlDeletedCounter = Counter.builder("url.deleted")
                .description("Total short URLs deleted")
                .register(meterRegistry);

        this.cacheInvalidationCounter = Counter.builder("cache.invalidation")
                .description("Total cache invalidation operations")
                .register(meterRegistry);

        // Register timers for latency
        this.caffeineCacheAccessTimer = Timer.builder("cache.access.caffeine")
                .description("Latency of Caffeine cache access")
                .register(meterRegistry);

        this.redisCacheAccessTimer = Timer.builder("cache.access.redis")
                .description("Latency of Redis cache access")
                .register(meterRegistry);

        this.databaseAccessTimer = Timer.builder("cache.access.database")
                .description("Latency of database access")
                .register(meterRegistry);

        log.info("CacheMetrics initialized with Micrometer");
    }

    // ============ LOOKUP & CACHE HIT RECORDING ============

    /**
     * Record a cache lookup attempt.
     */
    public void recordLookupAttempt(String shortCode) {
        totalRequests.incrementAndGet();
    }

    /**
     * Record a cache hit from a specific layer.
     *
     * @param layer One of: "caffeine", "redis", "database"
     */
    public void recordCacheHit(String layer) {
        switch (layer) {
            case "caffeine" -> cacheHitsCaffeine.incrementAndGet();
            case "redis" -> cacheHitsRedis.incrementAndGet();
            case "database" -> cacheHitsDatabase.incrementAndGet();
            default -> log.warn("Unknown cache layer: {}", layer);
        }
    }

    /**
     * Get cache hit ratio across all layers.
     *
     * @return Hit ratio as a percentage (0-100)
     */
    public double getCacheHitRatio() {
        long total = totalRequests.get();
        if (total == 0) {
            return 0.0;
        }
        long hits = cacheHitsCaffeine.get() + cacheHitsRedis.get();
        return (double) hits / total * 100;
    }

    // ============ COLLISION & FAILURE RECORDING ============

    /**
     * Record a short code collision detection.
     */
    public void recordCollision(String shortCode) {
        collisionsDetected.incrementAndGet();
        log.error("Short code collision detected: {}", shortCode);
    }

    /**
     * Record circuit breaker entering OPEN state.
     */
    public void recordCircuitBreakerOpen() {
        circuitBreakerOpenCount.incrementAndGet();
        log.warn("Redis circuit breaker opened");
    }

    /**
     * Record a failure to write to Redis.
     */
    public void recordCacheWriteFailure(String layer) {
        if ("redis".equals(layer)) {
            redisWriteFailures.incrementAndGet();
        }
        meterRegistry.counter("cache.write.failure", "layer", layer).increment();
    }

    // ============ URL OPERATION RECORDING ============

    /**
     * Record URL creation.
     */
    public void recordUrlCreated(String shortCode) {
        urlCreatedCounter.increment();
        log.debug("URL created: {}", shortCode);
    }

    /**
     * Record URL update.
     */
    public void recordUrlUpdated(String shortCode) {
        urlUpdatedCounter.increment();
        log.debug("URL updated: {}", shortCode);
    }

    /**
     * Record URL deletion.
     */
    public void recordUrlDeleted(String shortCode) {
        urlDeletedCounter.increment();
        log.debug("URL deleted: {}", shortCode);
    }

    /**
     * Record a cache write operation.
     */
    public void recordCacheWrite(String layer) {
        meterRegistry.counter("cache.write", "layer", layer).increment();
    }

    /**
     * Record cache invalidation.
     */
    public void recordCacheInvalidation(String shortCode) {
        cacheInvalidationCounter.increment();
    }

    // ============ LATENCY RECORDING ============

    /**
     * Record latency for Caffeine cache access.
     */
    public Timer.Sample startCaffeineCacheTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordCaffeineCacheAccess(Timer.Sample sample) {
        sample.stop(caffeineCacheAccessTimer);
    }

    /**
     * Record latency for Redis cache access.
     */
    public Timer.Sample startRedisCacheTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordRedisCacheAccess(Timer.Sample sample) {
        sample.stop(redisCacheAccessTimer);
    }

    /**
     * Record latency for database access.
     */
    public Timer.Sample startDatabaseTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordDatabaseAccess(Timer.Sample sample) {
        sample.stop(databaseAccessTimer);
    }

    // ============ METRICS SNAPSHOT (FOR MONITORING) ============

    /**
     * Get a snapshot of all metrics for debugging/monitoring.
     *
     * @return Map of metric names to values
     */
    public CacheMetricsSnapshot getMetricsSnapshot() {
        long total = totalRequests.get();
        long totalHits = cacheHitsCaffeine.get() + cacheHitsRedis.get() + cacheHitsDatabase.get();
        double hitRate = total > 0 ? (double) totalHits / total * 100 : 0;

        return CacheMetricsSnapshot.builder()
                .totalRequests(total)
                .cacheHitsCaffeine(cacheHitsCaffeine.get())
                .cacheHitsRedis(cacheHitsRedis.get())
                .cacheHitsDatabase(cacheHitsDatabase.get())
                .totalHits(totalHits)
                .cacheHitRatioPercent(String.format("%.2f%%", hitRate))
                .collisionsDetected(collisionsDetected.get())
                .circuitBreakerOpenCount(circuitBreakerOpenCount.get())
                .redisWriteFailures(redisWriteFailures.get())
                .build();
    }

    // ============ NESTED SNAPSHOT CLASS ============

    @lombok.Data
    @lombok.Builder
    public static class CacheMetricsSnapshot {
        private long totalRequests;
        private long cacheHitsCaffeine;
        private long cacheHitsRedis;
        private long cacheHitsDatabase;
        private long totalHits;
        private String cacheHitRatioPercent;
        private long collisionsDetected;
        private long circuitBreakerOpenCount;
        private long redisWriteFailures;
    }
}
